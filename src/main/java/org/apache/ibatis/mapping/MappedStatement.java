/*
 *    Copyright 2009-2025 the original author or authors.
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
package org.apache.ibatis.mapping;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.keygen.NoKeyGenerator;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.reflection.ParamNameResolver;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.session.Configuration;

/**
 * @author Clinton Begin
 */
// 在 MyBatis 的架构中，MappedStatement 是一个至关重要的类。如果把 MyBatis 比作一个运行引擎，那么 MappedStatement 就是这个引擎的“指令集”。
// MappedStatement 的作用是代表 SQL 映射语句在内存中的完整定义。
// 在 XML 映射文件中定义的每一个 <select>, <insert>, <update>, <delete> 标签，或者在 Mapper 接口中使用注解定义的 SQL 语句，在 MyBatis 启动并解析配置后，都会在全局
// Configuration 对象中对应生成一个 MappedStatement 实例。
// 它封装了执行一条 SQL 所需的所有信息，包括：
// SQL 内容：如何根据参数生成最终的 SQL 字符串。
// 输入映射：如何处理传入的参数。
// 输出映射：如何将结果集（ResultSet）转换为 Java 对象。
// 行为策略：是否开启缓存、超时时间、主键生成方案等。
public final class MappedStatement {
  // 记录该语句来源的资源路径（如 com/example/UserMapper.xml）
  private String resource;
  // 引用全局配置对象
  private Configuration configuration;
  // 语句的唯一标识，通常是 namespace + methodId。
  private String id;
  // 驱动提示：每次从数据库读取的数据行数。
  private Integer fetchSize;
  // 数据库查询超时时间（秒）。
  private Integer timeout;
  // 语句执行类型（STATEMENT直接执行, PREPARED预处理, CALLABLE存储过程）。
  private StatementType statementType;
  // 结果集滚动类型（如 FORWARD_ONLY, SCROLL_INSENSITIVE 等）。
  private ResultSetType resultSetType;
  // 关键属性。负责根据运行时参数生成 BoundSql。
  private SqlSource sqlSource;
  // 二级缓存引用。如果该语句配置了缓存，则指向对应的 Cache 实现。
  private Cache cache;
  // 参数映射定义（主要用于存储过程或老式配置）。
  private ParameterMap parameterMap;
  // 结果映射列表。定义了如何将数据库列映射到 Java 属性。
  private List<ResultMap> resultMaps;
  // 是否在执行该语句后强制刷新缓存（默认 update/insert/delete 为 true）。
  private boolean flushCacheRequired;
  // 该语句是否使用二级缓存（默认 select 为 true）。
  private boolean useCache;
  // 仅针对嵌套查询，是否按结果排序。
  private boolean resultOrdered;
  // SQL 命令类型（UNKNOWN, INSERT, UPDATE, DELETE, SELECT, FLUSH）。
  private SqlCommandType sqlCommandType;
  // 主键生成器（如 Jdbc3KeyGenerator 获取自增主键）。
  private KeyGenerator keyGenerator;
  // 对应 Java 实体类中接收主键的属性名。
  private String[] keyProperties;
  // 对应数据库中作为主键的列名。
  private String[] keyColumns;
  // 标记位，指示结果映射中是否包含嵌套查询或集合。
  private boolean hasNestedResultMaps;
  // 多数据库支持的标识。
  private String databaseId;
  // 该语句专用的日志对象。
  private Log statementLog;
  // 脚本语言驱动（处理动态 SQL 的逻辑引擎）。
  private LanguageDriver lang;
  // 多结果集处理时的结果集名称。
  private String[] resultSets;
  // 参数名解析器，用于处理方法参数到 SQL 的映射。
  private ParamNameResolver paramNameResolver;
  // 标记是否允许在脏读环境下执行查询。
  private boolean dirtySelect;

  MappedStatement() {
    // constructor disabled
  }

  public static class Builder {
    private final MappedStatement mappedStatement = new MappedStatement();

    public Builder(Configuration configuration, String id, SqlSource sqlSource, SqlCommandType sqlCommandType) {
      mappedStatement.configuration = configuration;
      mappedStatement.id = id;
      mappedStatement.sqlSource = sqlSource;
      mappedStatement.statementType = StatementType.PREPARED;
      mappedStatement.resultSetType = ResultSetType.DEFAULT;
      mappedStatement.parameterMap = ParameterMap.buildEmpty("defaultParameterMap", null);
      mappedStatement.resultMaps = new ArrayList<>();
      mappedStatement.sqlCommandType = sqlCommandType;
      mappedStatement.keyGenerator = configuration.isUseGeneratedKeys() && SqlCommandType.INSERT.equals(sqlCommandType)
          ? Jdbc3KeyGenerator.INSTANCE : NoKeyGenerator.INSTANCE;
      String logId = id;
      if (configuration.getLogPrefix() != null) {
        logId = configuration.getLogPrefix() + id;
      }
      mappedStatement.statementLog = LogFactory.getLog(logId);
      mappedStatement.lang = configuration.getDefaultScriptingLanguageInstance();
    }

    public Builder resource(String resource) {
      mappedStatement.resource = resource;
      return this;
    }

    public String id() {
      return mappedStatement.id;
    }

    public Builder parameterMap(ParameterMap parameterMap) {
      mappedStatement.parameterMap = parameterMap;
      return this;
    }

    public Builder resultMaps(List<ResultMap> resultMaps) {
      mappedStatement.resultMaps = resultMaps;
      for (ResultMap resultMap : resultMaps) {
        mappedStatement.hasNestedResultMaps = mappedStatement.hasNestedResultMaps || resultMap.hasNestedResultMaps();
      }
      return this;
    }

    public Builder fetchSize(Integer fetchSize) {
      mappedStatement.fetchSize = fetchSize;
      return this;
    }

    public Builder timeout(Integer timeout) {
      mappedStatement.timeout = timeout;
      return this;
    }

    public Builder statementType(StatementType statementType) {
      mappedStatement.statementType = statementType;
      return this;
    }

    public Builder resultSetType(ResultSetType resultSetType) {
      mappedStatement.resultSetType = resultSetType == null ? ResultSetType.DEFAULT : resultSetType;
      return this;
    }

    public Builder cache(Cache cache) {
      mappedStatement.cache = cache;
      return this;
    }

    public Builder flushCacheRequired(boolean flushCacheRequired) {
      mappedStatement.flushCacheRequired = flushCacheRequired;
      return this;
    }

    public Builder useCache(boolean useCache) {
      mappedStatement.useCache = useCache;
      return this;
    }

    public Builder resultOrdered(boolean resultOrdered) {
      mappedStatement.resultOrdered = resultOrdered;
      return this;
    }

    public Builder keyGenerator(KeyGenerator keyGenerator) {
      mappedStatement.keyGenerator = keyGenerator;
      return this;
    }

    public Builder keyProperty(String keyProperty) {
      mappedStatement.keyProperties = delimitedStringToArray(keyProperty);
      return this;
    }

    public Builder keyColumn(String keyColumn) {
      mappedStatement.keyColumns = delimitedStringToArray(keyColumn);
      return this;
    }

    public Builder databaseId(String databaseId) {
      mappedStatement.databaseId = databaseId;
      return this;
    }

    public Builder lang(LanguageDriver driver) {
      mappedStatement.lang = driver;
      return this;
    }

    public Builder resultSets(String resultSet) {
      mappedStatement.resultSets = delimitedStringToArray(resultSet);
      return this;
    }

    public Builder dirtySelect(boolean dirtySelect) {
      mappedStatement.dirtySelect = dirtySelect;
      return this;
    }

    public Builder paramNameResolver(ParamNameResolver paramNameResolver) {
      mappedStatement.paramNameResolver = paramNameResolver;
      return this;
    }

    /**
     * Resul sets.
     *
     * @param resultSet
     *          the result set
     *
     * @return the builder
     *
     * @deprecated Use {@link #resultSets}
     */
    @Deprecated
    public Builder resulSets(String resultSet) {
      mappedStatement.resultSets = delimitedStringToArray(resultSet);
      return this;
    }

    public MappedStatement build() {
      assert mappedStatement.configuration != null;
      assert mappedStatement.id != null;
      assert mappedStatement.sqlSource != null;
      assert mappedStatement.lang != null;
      mappedStatement.resultMaps = Collections.unmodifiableList(mappedStatement.resultMaps);
      return mappedStatement;
    }
  }

  public KeyGenerator getKeyGenerator() {
    return keyGenerator;
  }

  public SqlCommandType getSqlCommandType() {
    return sqlCommandType;
  }

  public String getResource() {
    return resource;
  }

  public Configuration getConfiguration() {
    return configuration;
  }

  public String getId() {
    return id;
  }

  public boolean hasNestedResultMaps() {
    return hasNestedResultMaps;
  }

  public Integer getFetchSize() {
    return fetchSize;
  }

  public Integer getTimeout() {
    return timeout;
  }

  public StatementType getStatementType() {
    return statementType;
  }

  public ResultSetType getResultSetType() {
    return resultSetType;
  }

  public SqlSource getSqlSource() {
    return sqlSource;
  }

  public ParameterMap getParameterMap() {
    return parameterMap;
  }

  public List<ResultMap> getResultMaps() {
    return resultMaps;
  }

  public Cache getCache() {
    return cache;
  }

  public boolean isFlushCacheRequired() {
    return flushCacheRequired;
  }

  public boolean isUseCache() {
    return useCache;
  }

  public boolean isResultOrdered() {
    return resultOrdered;
  }

  public String getDatabaseId() {
    return databaseId;
  }

  public String[] getKeyProperties() {
    return keyProperties;
  }

  public String[] getKeyColumns() {
    return keyColumns;
  }

  public Log getStatementLog() {
    return statementLog;
  }

  public LanguageDriver getLang() {
    return lang;
  }

  public String[] getResultSets() {
    return resultSets;
  }

  public boolean isDirtySelect() {
    return dirtySelect;
  }

  public ParamNameResolver getParamNameResolver() {
    return paramNameResolver;
  }

  /**
   * Gets the resul sets.
   *
   * @return the resul sets
   *
   * @deprecated Use {@link #getResultSets()}
   */
  @Deprecated
  public String[] getResulSets() {
    return resultSets;
  }

  public BoundSql getBoundSql(Object parameterObject) {
    BoundSql boundSql = sqlSource.getBoundSql(parameterObject);
    List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
    if (parameterMappings == null || parameterMappings.isEmpty()) {
      boundSql = new BoundSql(configuration, boundSql.getSql(), parameterMap.getParameterMappings(), parameterObject);
    }

    // check for nested result maps in parameter mappings (issue #30)
    for (ParameterMapping pm : boundSql.getParameterMappings()) {
      String rmId = pm.getResultMapId();
      if (rmId != null) {
        ResultMap rm = configuration.getResultMap(rmId);
        if (rm != null) {
          hasNestedResultMaps |= rm.hasNestedResultMaps();
        }
      }
    }

    return boundSql;
  }

  private static String[] delimitedStringToArray(String in) {
    if (in == null || in.trim().length() == 0) {
      return null;
    }
    return in.split(",");
  }

}
