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
package org.apache.ibatis.transaction;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Wraps a database connection. Handles the connection lifecycle that comprises: its creation, preparation,
 * commit/rollback and close.
 *
 * @author Clinton Begin
 */
// Transaction 接口的主要作用是封装数据库连接（Connection）并管理其生命周期。
// 通过这个接口，MyBatis 将事务管理的具体实现与上层的 Executor（执行器）解耦。它的核心职责包括：
// 连接管理：负责数据库连接的获取、准备和关闭。
// 行为抽象：将不同环境下的事务行为（如 JDBC 原生管理、托管给 Spring 或容器管理）统一为一套 API。
// 生命周期控制：确保在业务操作完成后，连接能够被正确地提交、回滚或关闭。
public interface Transaction {

  /**
   * Retrieve inner database connection.
   *
   * @return DataBase connection
   *
   * @throws SQLException
   *           the SQL exception
   */
  // 作用：获取当前事务持有的数据库连接对象。
  // 最核心的方法。实现类（如 JdbcTransaction）通常会在此方法中进行连接的延迟加载（Lazy Load）。
  // 即只有在真正需要执行 SQL 时，才会通过数据源（DataSource）获取连接，并负责设置连接的属性（如隔离级别、自动提交状态等）。
  Connection getConnection() throws SQLException;

  /**
   * Commit inner database connection.
   *
   * @throws SQLException
   *           the SQL exception
   */
  // 提交当前事务
  // 将该连接上所有未提交的更改持久化到数据库中。在不同的实现中，行为可能不同：
  void commit() throws SQLException;

  /**
   * Rollback inner database connection.
   *
   * @throws SQLException
   *           the SQL exception
   */
  void rollback() throws SQLException;

  /**
   * Close inner database connection.
   *
   * @throws SQLException
   *           the SQL exception
   */
  void close() throws SQLException;

  /**
   * Get transaction timeout if set.
   *
   * @return the timeout
   *
   * @throws SQLException
   *           the SQL exception
   */
  Integer getTimeout() throws SQLException;

}
