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
package org.apache.ibatis.mapping;

import javax.sql.DataSource;

import org.apache.ibatis.transaction.TransactionFactory;

/**
 * @author Clinton Begin
 */
// 在 MyBatis 的配置体系中，Environment 类是一个非常关键的配置载体。它对应了我们在 mybatis-config.xml 配置文件中经常看到的 <environment> 标签。
// Environment（环境）类的主要作用是定义数据库连接的核心要素。
// MyBatis 支持配置多个环境（例如：开发环境 development、测试环境 test、生产环境 production），但每个 SqlSessionFactory 实例只能选择其中一个环境。Environment
// 类将以下三者绑定在一起：
// 环境标识：给这个配置起个名字。
// 事务管理：规定如何处理 commit 和 rollback。
// 数据源：规定如何连接数据库。
public final class Environment {
  // 环境的唯一标识符。
  // 对应 XML：<environment id="development"> 中的 id 属性。
  private final String id;
  // 事务工厂实例。用于创建处理数据库事务的 Transaction 对象。
  private final TransactionFactory transactionFactory;
  // 数据源实例。用于获取数据库物理连接。
  private final DataSource dataSource;

  public Environment(String id, TransactionFactory transactionFactory, DataSource dataSource) {
    if (id == null) {
      throw new IllegalArgumentException("Parameter 'id' must not be null");
    }
    if (transactionFactory == null) {
      throw new IllegalArgumentException("Parameter 'transactionFactory' must not be null");
    }
    this.id = id;
    if (dataSource == null) {
      throw new IllegalArgumentException("Parameter 'dataSource' must not be null");
    }
    this.transactionFactory = transactionFactory;
    this.dataSource = dataSource;
  }

  public static class Builder {
    private final String id;
    private TransactionFactory transactionFactory;
    private DataSource dataSource;

    public Builder(String id) {
      this.id = id;
    }

    public Builder transactionFactory(TransactionFactory transactionFactory) {
      this.transactionFactory = transactionFactory;
      return this;
    }

    public Builder dataSource(DataSource dataSource) {
      this.dataSource = dataSource;
      return this;
    }

    public String id() {
      return this.id;
    }

    public Environment build() {
      return new Environment(this.id, this.transactionFactory, this.dataSource);
    }

  }

  public String getId() {
    return this.id;
  }

  public TransactionFactory getTransactionFactory() {
    return this.transactionFactory;
  }

  public DataSource getDataSource() {
    return this.dataSource;
  }

}
