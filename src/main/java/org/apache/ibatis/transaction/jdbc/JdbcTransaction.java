/*
 *    Copyright 2009-2024 the original author or authors.
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
package org.apache.ibatis.transaction.jdbc;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.session.TransactionIsolationLevel;
import org.apache.ibatis.transaction.Transaction;
import org.apache.ibatis.transaction.TransactionException;

/**
 * {@link Transaction} that makes use of the JDBC commit and rollback facilities directly. It relies on the connection
 * retrieved from the dataSource to manage the scope of the transaction. Delays connection retrieval until
 * getConnection() is called. Ignores commit or rollback requests when autocommit is on.
 *
 * @author Clinton Begin
 *
 * @see JdbcTransactionFactory
 */
// JdbcTransaction 是 MyBatis 对 Transaction 接口最常用的实现类。它是对 JDBC 原生事务管理行为的直接封装。
// JdbcTransaction 专门用于直接利用 JDBC 的提交和回滚机制 来管理数据库事务。
// 它的核心逻辑可以概括为：
// 直接控制：直接调用 java.sql.Connection 的 commit()、rollback() 和 close()。
// 延迟加载：它不会在初始化时就去获取数据库连接，而是等到真正需要执行 SQL（即调用 getConnection()）时，才从数据源获取。
// 状态保护：在关闭连接前，尝试恢复连接的自动提交状态，以确保连接回到连接池时处于干净的状态。
public class JdbcTransaction implements Transaction {

  private static final Log log = LogFactory.getLog(JdbcTransaction.class);
  // 当前事务持有的原生 JDBC 连接对象。
  protected Connection connection;
  // 数据库连接工厂。当 connection 为空时，通过它来获取新连接。
  protected DataSource dataSource;
  // 事务隔离级别（如：READ_COMMITTED）。
  protected TransactionIsolationLevel level;
  // 期望的自动提交状态。通常 MyBatis 设为 false 以便手动管理事务。
  protected boolean autoCommit;
  // 是否在关闭连接时跳过“重置自动提交”的操作（为了兼容某些特殊的数据库驱动，如 Sybase）。
  protected boolean skipSetAutoCommitOnClose;

  public JdbcTransaction(DataSource ds, TransactionIsolationLevel desiredLevel, boolean desiredAutoCommit) {
    this(ds, desiredLevel, desiredAutoCommit, false);
  }

  public JdbcTransaction(DataSource ds, TransactionIsolationLevel desiredLevel, boolean desiredAutoCommit,
      boolean skipSetAutoCommitOnClose) {
    dataSource = ds;
    level = desiredLevel;
    autoCommit = desiredAutoCommit;
    this.skipSetAutoCommitOnClose = skipSetAutoCommitOnClose;
  }

  public JdbcTransaction(Connection connection) {
    this.connection = connection;
  }

  // 获取连接。
  // 采用懒加载模式。如果当前 connection 属性为空，则调用 openConnection() 去创建一个，否则直接返回现有的连接。
  @Override
  public Connection getConnection() throws SQLException {
    if (connection == null) {
      openConnection();
    }
    return connection;
  }

  // 提交事务。
  // 只有当连接不为空，且当前不是自动提交模式时，才会执行 connection.commit()。如果 autoCommit 为 true，JDBC 会自动提交，MyBatis 则忽略此调用。
  @Override
  public void commit() throws SQLException {
    if (connection != null && !connection.getAutoCommit()) {
      if (log.isDebugEnabled()) {
        log.debug("Committing JDBC Connection [" + connection + "]");
      }
      connection.commit();
    }
  }

  // 回滚事务。
  // 同 commit，只有在非自动提交模式下，调用 connection.rollback()。
  @Override
  public void rollback() throws SQLException {
    if (connection != null && !connection.getAutoCommit()) {
      if (log.isDebugEnabled()) {
        log.debug("Rolling back JDBC Connection [" + connection + "]");
      }
      connection.rollback();
    }
  }

  // 关闭连接。
  // 在物理关闭连接前，先调用 resetAutoCommit() 尝试重置状态，然后执行 connection.close()。
  @Override
  public void close() throws SQLException {
    if (connection != null) {
      resetAutoCommit();
      if (log.isDebugEnabled()) {
        log.debug("Closing JDBC Connection [" + connection + "]");
      }
      connection.close();
    }
  }

  // 安全地设置连接的 autoCommit 状态。
  // 先检查连接当前的 autoCommit 状态，如果不符合预期，再执行设置操作。这避免了多余的数据库交互。
  protected void setDesiredAutoCommit(boolean desiredAutoCommit) {
    try {
      if (connection.getAutoCommit() != desiredAutoCommit) {
        if (log.isDebugEnabled()) {
          log.debug("Setting autocommit to " + desiredAutoCommit + " on JDBC Connection [" + connection + "]");
        }
        connection.setAutoCommit(desiredAutoCommit);
      }
    } catch (SQLException e) {
      // Only a very poorly implemented driver would fail here,
      // and there's not much we can do about that.
      throw new TransactionException(
          "Error configuring AutoCommit.  " + "Your driver may not support getAutoCommit() or setAutoCommit(). "
              + "Requested setting: " + desiredAutoCommit + ".  Cause: " + e,
          e);
    }
  }

  // 重置连接状态。
  // 核心背景：某些数据库（如某些版本的 Oracle）在仅执行 SELECT 语句后也会开启事务。
  // 如果在关闭前不进行提交/回滚或将 autoCommit 设为 true，连接回到连接池后可能会导致死锁或状态混乱。
  protected void resetAutoCommit() {
    try {
      if (!skipSetAutoCommitOnClose && !connection.getAutoCommit()) {
        // MyBatis does not call commit/rollback on a connection if just selects were performed.
        // Some databases start transactions with select statements
        // and they mandate a commit/rollback before closing the connection.
        // A workaround is setting the autocommit to true before closing the connection.
        // Sybase throws an exception here.
        if (log.isDebugEnabled()) {
          log.debug("Resetting autocommit to true on JDBC Connection [" + connection + "]");
        }
        connection.setAutoCommit(true);
      }
    } catch (SQLException e) {
      if (log.isDebugEnabled()) {
        log.debug("Error resetting autocommit to true " + "before closing the connection.  Cause: " + e);
      }
    }
  }

  // 实际从数据源获取连接并配置环境。
  // 如果指定了隔离级别 level，则调用 setTransactionIsolation。
  protected void openConnection() throws SQLException {
    if (log.isDebugEnabled()) {
      log.debug("Opening JDBC Connection");
    }
    connection = dataSource.getConnection();
    if (level != null) {
      connection.setTransactionIsolation(level.getLevel());
    }
    setDesiredAutoCommit(autoCommit);
  }

  // 获取超时时间。
  // 该类简单地返回 null，表示 JdbcTransaction 本身不直接管理事务超时。
  @Override
  public Integer getTimeout() throws SQLException {
    return null;
  }

}
