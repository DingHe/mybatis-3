/*
 *    Copyright 2009-2023 the original author or authors.
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

import java.sql.SQLException;
import java.util.List;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;

/**
 * @author Clinton Begin
 */
// 在 MyBatis 的架构中，Executor（执行器）是核心调度中心。如果说 SqlSession 是面向用户的门面，那么 Executor 就是真正干活的底层劳动力。
// Executor 位于 MyBatis 执行流程的核心层。它的主要职责是：负责 SQL 语句的生成、查询缓存的维护、事务的管理以及最终结果集的调度。
// 其具体作用包括：
// SQL 执行：调用 StatementHandler 来处理 JDBC 的编译与执行（增删改查）。
// 事务管理：维护一个 Transaction 对象，处理数据库连接的提交与回滚。
// 缓存维护：管理一级缓存（Local Cache）和二级缓存（通过装饰器模式）。
// 延迟加载协同：处理延迟加载属性的占位与加载逻辑。
public interface Executor {
  // 表示当前查询不需要特殊的结果处理器，通常直接返回结果列表。
  ResultHandler NO_RESULT_HANDLER = null;

  // 作用：执行 INSERT、UPDATE、DELETE 语句。
  // 参数：ms 是映射语句对象，parameter 是用户传入的参数。
  int update(MappedStatement ms, Object parameter) throws SQLException;

  // 作用：这是查询的最底层方法。它直接接收 CacheKey（缓存键）和 BoundSql（SQL 文本）。
  <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler,
      CacheKey cacheKey, BoundSql boundSql) throws SQLException;

  // 作用：常用的查询入口。它会先计算 CacheKey，再调用上面的重载方法。
  <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler)
      throws SQLException;

  // 作用：流式查询，返回一个 Cursor 对象，适用于大数据量处理，避免一次性加载到内存。
  <E> Cursor<E> queryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds) throws SQLException;

  // 作用：刷新批处理语句。主要用于 BatchExecutor，将缓存在客户端的批量更新语句真正发送到数据库执行。
  List<BatchResult> flushStatements() throws SQLException;

  // 作用：提交事务。参数 required 决定是否必须执行提交动作。
  void commit(boolean required) throws SQLException;

  // 作用：回滚事务。
  void rollback(boolean required) throws SQLException;

  // 作用：根据 SQL ID、参数、分页等信息生成唯一的缓存 Key，用于一级缓存和二级缓存。
  CacheKey createCacheKey(MappedStatement ms, Object parameterObject, RowBounds rowBounds, BoundSql boundSql);

  // 作用：判断当前的查询是否已经存在于缓存中。
  boolean isCached(MappedStatement ms, CacheKey key);

  // 作用：手动清理一级缓存（Session 级别的缓存）。
  void clearLocalCache();

  // 作用：延迟加载的异步处理。当查询结果的一级缓存中还没有对应数据时，将该加载请求“挂起”，等后续一级缓存填充后再加载。
  void deferLoad(MappedStatement ms, MetaObject resultObject, String property, CacheKey key, Class<?> targetType);

  // 作用：获取执行器当前持有的事务对象。
  Transaction getTransaction();

  // 作用：关闭执行器。forceRollback 参数决定在关闭前是否强制回滚。
  void close(boolean forceRollback);

  // 作用：检查执行器是否已关闭。
  boolean isClosed();

  // 作用：设置执行器的包装类。MyBatis 内部使用装饰器模式（如 CachingExecutor 装饰 SimpleExecutor），此方法用于建立这种关联。
  void setExecutorWrapper(Executor executor);

}
