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
package org.apache.ibatis.type;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @author Clinton Begin
 */
// 在 MyBatis 的架构中，TypeHandler（类型处理器）是连接 Java 对象与 数据库字段 之间的“翻译官”。
// TypeHandler 处于数据交互的最前线，其核心作用是：负责 Java 类型（JavaType）与 JDBC 类型（JdbcType）之间的双向转换。
// 具体职责包括：
// 入参映射：在执行 SQL 之前，将 Java 对象的属性值转换为数据库能理解的参数，并通过 JDBC 的 PreparedStatement 进行设置。
// 结果映射：在执行 SQL 之后，将 JDBC 返回的 ResultSet（结果集）或 CallableStatement（存储过程）中的列数据转换为 Java 对象。
public interface TypeHandler<T> {
  // 作用：将 Java 类型的参数绑定到 SQL 预编译语句中。
  // i：参数在 SQL 语句中的索引位置（从 1 开始）。
  // parameter：要设置的 Java 对象值（类型为泛型 T）。
  // jdbcType：该参数对应的 JDBC 类型（如 VARCHAR, INTEGER 等），用于精细化控制转换逻辑。
  void setParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType) throws SQLException;

  /**
   * Gets the result.
   *
   * @param rs
   *          the rs
   * @param columnName
   *          Column name, when configuration <code>useColumnLabel</code> is <code>false</code>
   *
   * @return the result
   *
   * @throws SQLException
   *           the SQL exception
   */
  // 由于数据库返回结果的形式不同，MyBatis 定义了三种获取方式：
  // 作用：根据列名从结果集中获取数据。
  T getResult(ResultSet rs, String columnName) throws SQLException;

  // 作用：根据列索引从结果集中获取数据。
  T getResult(ResultSet rs, int columnIndex) throws SQLException;

  // 作用：从存储过程的输出参数中获取数据。
  T getResult(CallableStatement cs, int columnIndex) throws SQLException;

}
