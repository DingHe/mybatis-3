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
package org.apache.ibatis.transaction;

import java.sql.Connection;
import java.util.Properties;

import javax.sql.DataSource;

import org.apache.ibatis.session.TransactionIsolationLevel;

/**
 * Creates {@link Transaction} instances.
 *
 * @author Clinton Begin
 */
// 在 MyBatis 的设计模式中，TransactionFactory 扮演的是 工厂模式（Factory Pattern） 中的抽象工厂角色。它是产生 Transaction 对象的摇篮。
// 主要作用是定义创建事务实例的标准规范。
// 由于 MyBatis 支持多种事务管理方式（如 JDBC、MANAGED，或通过 Spring 集成自定义的事务），框架需要一种统一的方式来获取事务对象，而不需要关心具体的实现细节。
// 解耦：通过工厂接口，MyBatis 的核心执行引擎（Executor）可以只依赖 TransactionFactory 来获取事务，而不需要知道具体创建的是 JdbcTransaction 还是 ManagedTransaction。
// 配置化：在 mybatis-config.xml 中配置 <transactionManager type="JDBC"/> 时，MyBatis 会根据 type 找到对应的工厂类进行实例化。
public interface TransactionFactory {

  /**
   * Sets transaction factory custom properties.
   *
   * @param props
   *          the new properties
   */
  // 初始化工厂的自定义属性。
  // 这是一个 Java 8 引入的默认方法（default method）。
  // 当我们在配置文件中为事务管理器配置 <property> 标签时，MyBatis 会将这些参数封装成 Properties 对象并传递给此方法。
  default void setProperties(Properties props) {
    // NOP
  }

  /**
   * Creates a {@link Transaction} out of an existing connection.
   *
   * @param conn
   *          Existing database connection
   *
   * @return Transaction
   *
   * @since 3.1.0
   */
  // 基于一个已存在的连接创建事务对象。
  // 当你已经有一个 java.sql.Connection 实例，并希望 MyBatis 在这个现有连接的基础上进行事务管理时调用此方法。
  Transaction newTransaction(Connection conn);

  /**
   * Creates a {@link Transaction} out of a datasource.
   *
   * @param dataSource
   *          DataSource to take the connection from
   * @param level
   *          Desired isolation level
   * @param autoCommit
   *          Desired autocommit
   *
   * @return Transaction
   *
   * @since 3.1.0
   */
  // 基于数据源创建事务对象。
  // 这是 MyBatis 最常用的创建事务的方式。
  Transaction newTransaction(DataSource dataSource, TransactionIsolationLevel level, boolean autoCommit);

}
