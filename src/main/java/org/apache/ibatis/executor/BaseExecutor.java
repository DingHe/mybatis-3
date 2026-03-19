/*
 *    Copyright 2009-2026 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.executor;

import static org.apache.ibatis.executor.ExecutionPlaceholder.EXECUTION_PLACEHOLDER;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.cache.impl.PerpetualCache;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.statement.StatementUtil;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.logging.jdbc.ConnectionLogger;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.ParamNameResolver;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;
import org.apache.ibatis.type.TypeHandlerRegistry;

/**
 * @author Clinton Begin
 */
// 在 MyBatis 的执行引擎中，BaseExecutor 是一个极其重要的抽象基类。
// 它实现了 Executor 接口，采用了模板方法模式，为所有的具体执行器（如 SimpleExecutor, ReuseExecutor, BatchExecutor）提供了通用的逻辑基础
// BaseExecutor 的核心作用是管理一级缓存（本地缓存）和事务，并定义了 SQL 执行的标准流程。
// 它屏蔽了缓存管理、事务提交/回滚、延迟加载等复杂且通用的底层细节，将最核心的数据库操作（如真正执行查询、更新）留给子类通过 doUpdate、doQuery 等抽象方法去实现。
public abstract class BaseExecutor implements Executor {

  private static final Log log = LogFactory.getLog(BaseExecutor.class);
  // 当前执行器关联的事务对象，负责获取连接、提交和回滚。
  protected Transaction transaction;
  // 指向执行器包装类。在装饰器模式中（如开启二级缓存时），它可能指向 CachingExecutor。
  protected Executor wrapper;
  // 一个线程安全的队列，存储需要延迟加载的操作。
  protected ConcurrentLinkedQueue<DeferredLoad> deferredLoads;
  // 一级缓存的核心实现（PerpetualCache）。其生命周期与 SqlSession 相同。
  protected PerpetualCache localCache;
  // 专门用于存储存储过程（Callable）输出参数的本地缓存。
  protected PerpetualCache localOutputParameterCache;
  // MyBatis 全局配置。
  protected Configuration configuration;
  // 查询堆栈层数。用于处理嵌套查询（防止循环调用）和确定何时清除缓存。
  protected int queryStack;
  // 标记执行器是否已关闭。
  private boolean closed;

  protected BaseExecutor(Configuration configuration, Transaction transaction) {
    this.transaction = transaction;
    this.deferredLoads = new ConcurrentLinkedQueue<>();
    this.localCache = new PerpetualCache("LocalCache");
    this.localOutputParameterCache = new PerpetualCache("LocalOutputParameterCache");
    this.closed = false;
    this.configuration = configuration;
    this.wrapper = this;
  }

  @Override
  public Transaction getTransaction() {
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }
    return transaction;
  }

  @Override
  public void close(boolean forceRollback) {
    try {
      try {
        rollback(forceRollback);
      } finally {
        if (transaction != null) {
          transaction.close();
        }
      }
    } catch (SQLException e) {
      // Ignore. There's nothing that can be done at this point.
      log.warn("Unexpected exception on closing transaction.  Cause: " + e);
    } finally {
      transaction = null;
      deferredLoads = null;
      localCache = null;
      localOutputParameterCache = null;
      closed = true;
    }
  }

  @Override
  public boolean isClosed() {
    return closed;
  }

  // update 方法是 BaseExecutor 中处理所有写操作（INSERT、UPDATE、DELETE）的核心入口。
  // 它体现了 MyBatis 处理“写请求”时的两个基本原则：上下文追踪和缓存一致性。
  // 在 MyBatis 中，所有的增删改操作最终都会调用 Executor 的 update 方法。BaseExecutor 作为一个基类，在这里主要负责：
  // 异常上下文准备：记录当前执行的 SQL 信息，以便出错时能提供详细的错误日志。
  // 状态检查：确保执行器未关闭。
  // 维护一级缓存：在执行任何写操作之前，必须强制清空本地一级缓存。这是为了防止脏读，保证后续的查询能拿到数据库中最真实的数据。
  @Override
  public int update(MappedStatement ms, Object parameter) throws SQLException {
    // 利用 ThreadLocal 存储当前线程的执行状态。
    ErrorContext.instance().resource(ms.getResource()).activity("executing an update").object(ms.getId());
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }
    clearLocalCache();
    return doUpdate(ms, parameter);
  }

  @Override
  public List<BatchResult> flushStatements() throws SQLException {
    return flushStatements(false);
  }

  public List<BatchResult> flushStatements(boolean isRollBack) throws SQLException {
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }
    return doFlushStatements(isRollBack);
  }

  // 核心任务是为即将进行的查询操作准备好所有必要的“物料”，特别是生成用于一级缓存识别的唯一标识——CacheKey
  // 在 MyBatis 执行查询时，并不能直接拿着 SQL 去数据库。为了支持一级缓存（Local Cache），MyBatis 必须先判断：“在当前这个 SqlSession 中，我之前是否用同样的条件查过完全一样的数据？”
  //
  @Override
  public <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler)
      throws SQLException {
    // 解析 SQL：获取包含参数映射的最终 SQL 语句（BoundSql）。
    BoundSql boundSql = ms.getBoundSql(parameter);
    // 生成指纹：根据 SQL、参数、分页等信息计算出一个“指纹”（CacheKey）。
    CacheKey key = createCacheKey(ms, parameter, rowBounds, boundSql);
    // 将这些物料传递给更有深度、负责处理缓存逻辑的重载 query 方法。
    return query(ms, parameter, rowBounds, resultHandler, key, boundSql);
  }

  @SuppressWarnings("unchecked")
  // MyBatis 一级缓存（Session 级别缓存）的核心实现逻辑。它决定了数据是从内存直接返回，还是去敲数据库的大门。
  @Override
  public <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler,
      CacheKey key, BoundSql boundSql) throws SQLException {
    ErrorContext.instance().resource(ms.getResource()).activity("executing a query").object(ms.getId());
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }
    // 如果配置了 flushCache="true"（通常在 <select> 标签中显式设置），且当前不是嵌套查询（栈深为 0），则清空缓存
    if (queryStack == 0 && ms.isFlushCacheRequired()) {
      clearLocalCache();
    }
    List<E> list;
    try {
      queryStack++;
      // 如果用户自定义了 ResultHandler，MyBatis 会禁用一级缓存（即 list = null），强制去数据库。
      // 因为 MyBatis 无法预测自定义处理器会如何操作数据，为了安全不走缓存。
      list = resultHandler == null ? (List<E>) localCache.getObject(key) : null;
      if (list != null) {
        // 命中缓存：如果缓存有值，且是存储过程，需要将缓存中的 OUT 参数值写回到当前的 parameter 对象中。
        handleLocallyCachedOutputParameters(ms, key, parameter, boundSql);
      } else {
        list = queryFromDatabase(ms, parameter, rowBounds, resultHandler, key, boundSql);
      }
    } finally {
      queryStack--;
    }
    // queryStack == 0：关键点。只有当最外层查询执行完毕后，才会执行以下收尾工作。
    if (queryStack == 0) {
      for (DeferredLoad deferredLoad : deferredLoads) {
        deferredLoad.load();
      }
      // issue #601
      deferredLoads.clear();
      // 如果是 SESSION（默认）：一级缓存在整个 SqlSession 生命周期内有效。
      // 如果是 STATEMENT：一级缓存退化。每次查询结束后立即清空。这常用于分布式环境下，防止同一事务中多次查询由于缓存导致的数据不一致（issue #482）
      if (configuration.getLocalCacheScope() == LocalCacheScope.STATEMENT) {
        // issue #482
        clearLocalCache();
      }
    }
    return list;
  }

  // MyBatis 流式查询（Cursor） 接口的实现。它与普通的 query 方法最大的区别在于：它完全绕过了一级缓存，并且返回的是一个延迟加载的迭代器。
  @Override
  public <E> Cursor<E> queryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds) throws SQLException {
    BoundSql boundSql = ms.getBoundSql(parameter);
    return doQueryCursor(ms, parameter, rowBounds, boundSql);
  }

  @Override
  public void deferLoad(MappedStatement ms, MetaObject resultObject, String property, CacheKey key,
      Class<?> targetType) {
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }
    DeferredLoad deferredLoad = new DeferredLoad(resultObject, property, key, localCache, configuration, targetType);
    if (deferredLoad.canLoad()) {
      deferredLoad.load();
    } else {
      deferredLoads.add(new DeferredLoad(resultObject, property, key, localCache, configuration, targetType));
    }
  }

  @Override
  public CacheKey createCacheKey(MappedStatement ms, Object parameterObject, RowBounds rowBounds, BoundSql boundSql) {
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }
    CacheKey cacheKey = new CacheKey();
    cacheKey.update(ms.getId());
    cacheKey.update(rowBounds.getOffset());
    cacheKey.update(rowBounds.getLimit());
    cacheKey.update(boundSql.getSql());
    List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
    TypeHandlerRegistry typeHandlerRegistry = ms.getConfiguration().getTypeHandlerRegistry();
    // mimic DefaultParameterHandler logic
    MetaObject metaObject = null;
    for (ParameterMapping parameterMapping : parameterMappings) {
      if (parameterMapping.getMode() != ParameterMode.OUT) {
        Object value;
        String propertyName = parameterMapping.getProperty();
        if (parameterMapping.hasValue()) {
          value = parameterMapping.getValue();
        } else if (boundSql.hasAdditionalParameter(propertyName)) {
          value = boundSql.getAdditionalParameter(propertyName);
        } else if (parameterObject == null) {
          value = null;
        } else {
          ParamNameResolver paramNameResolver = ms.getParamNameResolver();
          if (paramNameResolver != null
              && typeHandlerRegistry.hasTypeHandler(paramNameResolver.getType(paramNameResolver.getNames()[0]))
              || typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
            value = parameterObject;
          } else {
            if (metaObject == null) {
              metaObject = configuration.newMetaObject(parameterObject);
            }
            value = metaObject.getValue(propertyName);
          }
        }
        cacheKey.update(value);
      }
    }
    if (configuration.getEnvironment() != null) {
      // issue #176
      cacheKey.update(configuration.getEnvironment().getId());
    }
    return cacheKey;
  }

  @Override
  public boolean isCached(MappedStatement ms, CacheKey key) {
    return localCache.getObject(key) != null;
  }

  @Override
  public void commit(boolean required) throws SQLException {
    if (closed) {
      throw new ExecutorException("Cannot commit, transaction is already closed");
    }
    clearLocalCache();
    flushStatements();
    if (required) {
      transaction.commit();
    }
  }

  @Override
  public void rollback(boolean required) throws SQLException {
    if (!closed) {
      try {
        clearLocalCache();
        flushStatements(true);
      } finally {
        if (required) {
          transaction.rollback();
        }
      }
    }
  }

  // 清理缓存信息
  @Override
  public void clearLocalCache() {
    if (!closed) {
      localCache.clear();
      localOutputParameterCache.clear();
    }
  }

  protected abstract int doUpdate(MappedStatement ms, Object parameter) throws SQLException;

  protected abstract List<BatchResult> doFlushStatements(boolean isRollback) throws SQLException;

  protected abstract <E> List<E> doQuery(MappedStatement ms, Object parameter, RowBounds rowBounds,
      ResultHandler resultHandler, BoundSql boundSql) throws SQLException;

  protected abstract <E> Cursor<E> doQueryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds,
      BoundSql boundSql) throws SQLException;

  protected void closeStatement(Statement statement) {
    if (statement != null) {
      try {
        statement.close();
      } catch (SQLException e) {
        // ignore
      }
    }
  }

  /**
   * Apply a transaction timeout.
   *
   * @param statement
   *          a current statement
   *
   * @throws SQLException
   *           if a database access error occurs, this method is called on a closed <code>Statement</code>
   *
   * @since 3.4.0
   *
   * @see StatementUtil#applyTransactionTimeout(Statement, Integer, Integer)
   */
  protected void applyTransactionTimeout(Statement statement) throws SQLException {
    StatementUtil.applyTransactionTimeout(statement, statement.getQueryTimeout(), transaction.getTimeout());
  }

  // 专门为**存储过程（Stored Procedures）**设计的细节处理逻辑。
  // 解决了 MyBatis 一级缓存中的一个特殊问题：当命中缓存时，不仅要返回查询结果集，还必须恢复存储过程中的 OUT 或 INOUT 参数值。
  // 在 JDBC 中执行存储过程时，结果不仅可以通过 ResultSet（结果集）返回，还可以通过参数（Output Parameters）返回。
  // 挑战：MyBatis 的一级缓存 localCache 默认只存储 List 结果集。如果第二次查询命中了缓存，直接返回 List，那么调用方传入的 parameter 对象中的输出参数字段就会是空的。
  // 解决方案：MyBatis 引入了第二个缓存 localOutputParameterCache。当命中缓存时，该方法负责从这个专门的缓存中取出之前的参数值，并回填到当前请求的参数对象中。
  private void handleLocallyCachedOutputParameters(MappedStatement ms, CacheKey key, Object parameter,
      BoundSql boundSql) {
    // 只有当 SQL 语句类型为 CALLABLE（即存储过程）时，才需要处理输出参数。普通的 SELECT 语句会直接跳过。
    if (ms.getStatementType() == StatementType.CALLABLE) {
      final Object cachedParameter = localOutputParameterCache.getObject(key);
      // 使用相同的 CacheKey 从输出参数缓存中尝试获取数据。只有当缓存中有记录，且当前调用确实传入了参数对象时，才继续。
      if (cachedParameter != null && parameter != null) {
        // 使用 MyBatis 的反射工具类 MetaObject 包装缓存的参数和当前的参数。
        final MetaObject metaCachedParameter = configuration.newMetaObject(cachedParameter);
        final MetaObject metaParameter = configuration.newMetaObject(parameter);
        // 遍历与值回填
        for (ParameterMapping parameterMapping : boundSql.getParameterMappings()) {
          if (parameterMapping.getMode() != ParameterMode.IN) {
            final String parameterName = parameterMapping.getProperty();
            final Object cachedValue = metaCachedParameter.getValue(parameterName);
            metaParameter.setValue(parameterName, cachedValue);
          }
        }
      }
    }
  }

  // 当一级缓存（Local Cache）未命中时，MyBatis 就会调用 queryFromDatabase 方法。
  // 这个方法是真正向数据库“要数据”的最后一道防线，它不仅负责执行查询，还负责维护缓存状态的原子性。
  private <E> List<E> queryFromDatabase(MappedStatement ms, Object parameter, RowBounds rowBounds,
      ResultHandler resultHandler, CacheKey key, BoundSql boundSql) throws SQLException {
    List<E> list;
    // 在真正去数据库查之前，先往 localCache 里存入一个特殊的常量对象 EXECUTION_PLACEHOLDER。
    localCache.putObject(key, EXECUTION_PLACEHOLDER);
    try {
      list = doQuery(ms, parameter, rowBounds, resultHandler, boundSql);
    } finally {
      localCache.removeObject(key);
    }
    // 将数据库返回的 List 结果集正式放入一级缓存。
    localCache.putObject(key, list);
    if (ms.getStatementType() == StatementType.CALLABLE) {
      localOutputParameterCache.putObject(key, parameter);
    }
    return list;
  }

  protected Connection getConnection(Log statementLog) throws SQLException {
    Connection connection = transaction.getConnection();
    if (statementLog.isDebugEnabled()) {
      return ConnectionLogger.newInstance(connection, statementLog, queryStack);
    }
    return connection;
  }

  @Override
  public void setExecutorWrapper(Executor wrapper) {
    this.wrapper = wrapper;
  }

  // 存在是为了解决 MyBatis 中的**延迟加载（Lazy Loading）问题，特别是处理嵌套查询（Nested Select）**时的一级缓存同步。
  // 在执行复杂的关联查询时，MyBatis 可能需要先加载主对象，然后再去加载关联的子对象。
  // 场景：如果主对象 A 引用了对象 B，而 B 已经在当前 SqlSession 的一级缓存中（或者正在查询中），MyBatis 为了避免循环引用或重复查询，会先创建一个 DeferredLoad 对象“挂起”这个加载任务。
  // 核心逻辑：它像一个待办事项，记录了“要把哪个缓存 Key 的结果，填充到哪个对象的哪个属性上”。等到外层查询全部结束，再统一执行这些挂起的任务。
  private static class DeferredLoad {
    // 目标对象的包装类。它是反射工具，用于最终将值设置进主对象的属性中（例如 User 对象的 setRole）
    private final MetaObject resultObject;
    // 需要填充的属性名称（例如 "role"）
    private final String property;
    // 属性的目标类型（例如 Role.class）。
    private final Class<?> targetType;
    // 该关联查询对应的一级缓存键。
    private final CacheKey key;
    // 指向 BaseExecutor 的一级缓存，从中提取数据。
    private final PerpetualCache localCache;
    // MyBatis 的对象工厂，用于创建结果对象。
    private final ObjectFactory objectFactory;
    // 结果提取器。
    // 因为一级缓存中存储的通常是 List，而目标属性可能是单个对象也可能是集合，这个工具负责进行类型转换和提取。
    private final ResultExtractor resultExtractor;

    // issue #781
    public DeferredLoad(MetaObject resultObject, String property, CacheKey key, PerpetualCache localCache,
        Configuration configuration, Class<?> targetType) {
      this.resultObject = resultObject;
      this.property = property;
      this.key = key;
      this.localCache = localCache;
      this.objectFactory = configuration.getObjectFactory();
      this.resultExtractor = new ResultExtractor(configuration, objectFactory);
      this.targetType = targetType;
    }

    public boolean canLoad() {
      Object cached = localCache.getObject(key);
      return cached != null && cached != EXECUTION_PLACEHOLDER;
    }

    public void load() {
      @SuppressWarnings("unchecked")
      // we suppose we get back a List
      List<Object> list = (List<Object>) localCache.getObject(key);
      Object value = resultExtractor.extractObjectFromList(list, targetType);
      resultObject.setValue(property, value);
    }

  }

}
