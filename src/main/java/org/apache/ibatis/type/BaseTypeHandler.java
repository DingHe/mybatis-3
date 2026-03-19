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
package org.apache.ibatis.type;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.ibatis.executor.result.ResultMapException;
import org.apache.ibatis.session.Configuration;

/**
 * The base {@link TypeHandler} for references a generic type.
 * <p>
 * Important: Since 3.5.0, This class never call the {@link ResultSet#wasNull()} and {@link CallableStatement#wasNull()}
 * method for handling the SQL {@code NULL} value. In other words, {@code null} value handling should be performed on
 * subclass.
 *
 * @author Clinton Begin
 * @author Simone Tripodi
 * @author Kzuki Shimizu
 */
// 在 Java 对象与数据库之间，数据类型的定义是不一致的（例如 Java 的 String 对应数据库的 VARCHAR）。BaseTypeHandler 的主要作用是：
// 类型转换的桥梁：它实现了 TypeHandler 接口，负责将 Java 类型的数据设置到 SQL 语句的参数中，以及将数据库查询结果转换回 Java 类型。
// 统一异常处理：它在父类层面封装了常见的 SQLException 和 ResultMapException，开发者在子类中只需关注业务逻辑，不需要反复编写 try-catch。
// 空值（Null）处理：它定义了统一的逻辑来处理 SQL 中的 NULL 值。如果是 null，它会调用 JDBC 的 setNull；如果不为 null，则交给子类处理。
// 简化开发：通过模板方法模式（Template Method Pattern），它要求子类只需实现几个以 getNullableResult 和 setNonNullParameter 开头的抽象方法即可。
public abstract class BaseTypeHandler<T> implements TypeHandler<T> {

  /**
   * @deprecated Since 3.5.0 - See https://github.com/mybatis/mybatis-3/issues/1203. This field will remove future.
   */
  // 持有 MyBatis 的全局配置对象。通过它可以获取全局的设置（如映射规则、日志配置等）。
  @Deprecated
  protected Configuration configuration;

  /**
   * Sets the configuration.
   *
   * @param c
   *          the new configuration
   *
   * @deprecated Since 3.5.0 - See https://github.com/mybatis/mybatis-3/issues/1203. This property will remove future.
   */
  // 依赖注入方法，用于设置 configuration 属性。
  @Deprecated
  public void setConfiguration(Configuration c) {
    this.configuration = c;
  }

  // 设置 SQL 参数的入口。
  @Override
  public void setParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType) throws SQLException {
    if (parameter == null) {
      // 如果 parameter 为 null：检查是否指定了 jdbcType。如果没有指定，抛出异常（JDBC 要求设置 null 时必须指定类型）；如果指定了，调用 ps.setNull
      if (jdbcType == null) {
        throw new TypeException("JDBC requires that the JdbcType must be specified for all nullable parameters.");
      }
      try {
        ps.setNull(i, jdbcType.TYPE_CODE);
      } catch (SQLException e) {
        throw new TypeException("Error setting null for parameter #" + i + " with JdbcType " + jdbcType + " . "
            + "Try setting a different JdbcType for this parameter or a different jdbcTypeForNull configuration property. "
            + "Cause: " + e, e);
      }
    } else {
      // 如果 parameter 不为 null：调用抽象方法 setNonNullParameter 由子类完成具体的 JDBC 赋值
      try {
        setNonNullParameter(ps, i, parameter, jdbcType);
      } catch (Exception e) {
        throw new TypeException("Error setting non null for parameter #" + i + " with JdbcType " + jdbcType + " . "
            + "Try setting a different JdbcType for this parameter or a different configuration property. " + "Cause: "
            + e, e);
      }
    }
  }

  // 根据列名从结果集中获取数据
  // 直接调用子类实现的 getNullableResult(rs, columnName)。它封装了对异常的处理，将其转化为 ResultMapException
  @Override
  public T getResult(ResultSet rs, String columnName) throws SQLException {
    try {
      return getNullableResult(rs, columnName);
    } catch (Exception e) {
      throw new ResultMapException("Error attempting to get column '" + columnName + "' from result set.  Cause: " + e,
          e);
    }
  }

  // 根据列索引从结果集中获取数据
  @Override
  public T getResult(ResultSet rs, int columnIndex) throws SQLException {
    try {
      return getNullableResult(rs, columnIndex);
    } catch (Exception e) {
      throw new ResultMapException("Error attempting to get column #" + columnIndex + " from result set.  Cause: " + e,
          e);
    }
  }

  // 从存储过程的调用结果中，根据索引获取输出参数。
  @Override
  public T getResult(CallableStatement cs, int columnIndex) throws SQLException {
    try {
      return getNullableResult(cs, columnIndex);
    } catch (Exception e) {
      throw new ResultMapException(
          "Error attempting to get column #" + columnIndex + " from callable statement.  Cause: " + e, e);
    }
  }

  public abstract void setNonNullParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType)
      throws SQLException;

  /**
   * Gets the nullable result.
   *
   * @param rs
   *          the rs
   * @param columnName
   *          Column name, when configuration <code>useColumnLabel</code> is <code>false</code>
   *
   * @return the nullable result
   *
   * @throws SQLException
   *           the SQL exception
   */
  public abstract T getNullableResult(ResultSet rs, String columnName) throws SQLException;

  public abstract T getNullableResult(ResultSet rs, int columnIndex) throws SQLException;

  public abstract T getNullableResult(CallableStatement cs, int columnIndex) throws SQLException;

}
