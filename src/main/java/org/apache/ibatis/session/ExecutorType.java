/*
 *    Copyright 2009-2022 the original author or authors.
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
package org.apache.ibatis.session;

/**
 * @author Clinton Begin
 */
// 该枚举类的作用是决定 MyBatis 内部如何管理 Statement（语句对象）以及如何与数据库交互。
// 通过配置不同的执行器类型，开发者可以根据具体的业务场景（如：高频单条查询、重复执行相同 SQL、海量数据批量插入）来优化性能和资源消耗。
public enum ExecutorType {
  // 简单执行器
  // 这是 MyBatis 的默认值。
  // 每执行一次 SQL 语句，MyBatis 都会创建一个新的 PreparedStatement 对象。执行完毕后，立即关闭该对象。
  // 适用场景：绝大多数普通的增删改查操作。它的逻辑最直接，不存在对象复用带来的复杂性。
  SIMPLE,
  // 重用执行器
  // 重用预处理语句（PreparedStatements）
  // 内部维护了一个 Map，以 SQL 语句作为 Key。如果后续执行的 SQL 语句与之前执行过的完全相同，它会从缓存中取出之前创建好的 PreparedStatement 重新使用，而不是重新创建。
  // 适用场景：在同一个 SqlSession 中频繁执行相同 SQL 结构（但参数不同）的场景。它可以减少数据库服务器解析 SQL 频率以及驱动程序创建对象的开销。
  REUSE,
  // 批处理执行器
  // 不会立即执行 SQL，而是将 SQL 语句全部缓存在本地。只有当调用 SqlSession.flushStatements() 或 commit() 时，才会一次性将积压的所有 SQL 发送到数据库执行。
  // 适用场景：海量数据插入或更新（如一次性导入 10 万条数据）。
  BATCH

}
