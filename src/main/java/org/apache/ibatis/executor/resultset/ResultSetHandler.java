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
package org.apache.ibatis.executor.resultset;

import java.sql.CallableStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.apache.ibatis.cursor.Cursor;

/**
 * @author Clinton Begin
 */
// ResultSetHandler 是 MyBatis 执行器（Executor）体系中的四大核心处理器之一（另外三个是 StatementHandler、ParameterHandler 和 TypeHandler）。
// ResultSetHandler 的核心职责是 “结果映射”。
// 当数据库执行完 SQL 语句并返回 java.sql.ResultSet（结果集）后，ResultSetHandler 负责接手这个“原始数据”。它的作用包括：
// 数据解构：遍历 JDBC 的 ResultSet 对象。
// 对象实例化：根据 ResultMap 的定义，利用 ObjectFactory 创建 Java 对象。
// 属性填充：利用 TypeHandler 将数据库列值转换为 Java 类型，并通过反射（MetaObject）设置到对象属性中。
// 复杂关联处理：处理嵌套查询（Association/Collection）、懒加载以及多结果集（Multiple ResultSets）。
public interface ResultSetHandler {
  // 处理普通的查询结果集，将其转换为 Java 对象列表。
  // 入参：Statement 对象。这是已经执行完毕、持有结果集的 JDBC Statement。
  // 返回值：一个泛型 List，包含了所有映射好的实体对象。
  <E> List<E> handleResultSets(Statement stmt) throws SQLException;

  // 将结果集处理为“游标”对象（MyBatis 3.4.0+ 引入）。
  <E> Cursor<E> handleCursorResultSets(Statement stmt) throws SQLException;

  // 处理存储过程的 输出参数（OUT Parameters）
  void handleOutputParameters(CallableStatement cs) throws SQLException;

}
