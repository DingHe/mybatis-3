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

import java.sql.ResultSet;

import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

/**
 * @author Clinton Begin
 */
// 详细描述了 SQL 语句中某一个占位符（即 #{}）与 Java 对象属性之间的映射规则。
// ParameterMapping 的核心作用是：定义如何将 Java 环境中的一个参数值转换并绑定到 JDBC 的 PreparedStatement 中。
// 当 MyBatis 解析 SQL 语句中的 #{property, jdbcType=VARCHAR...} 时，每一个这样的占位符都会被解析为一个 ParameterMapping 实例。它是 BoundSql
// 对象的重要组成部分，确保了：
// 参数定位：知道该从 Java 对象的哪个属性获取值。
// 类型转换：知道该使用哪个 TypeHandler 进行类型转换。
// 存储过程支持：描述参数是输入（IN）、输出（OUT）还是输入输出（INOUT）模式。
public class ParameterMapping {
  // 静态常量，用于标记 value 属性是否被设置过（区分 null 值）。
  private static final Object UNSET = new Object();
  // 引用 MyBatis 的全局配置对象。
  private Configuration configuration;
  // 属性名。对应 #{} 中的名称，用于从实参对象中提取值。
  private String property;
  // 参数模式。默认为 IN。在存储过程中支持 OUT 和 INOUT。
  private ParameterMode mode;
  // Java 类型。该参数在 Java 对象中的类型，默认为 Object.class。
  private Class<?> javaType = Object.class;
  // JDBC 类型。数据库对应的列类型（如 VARCHAR, TIMESTAMP）。
  private JdbcType jdbcType;
  // 数值精度。主要用于处理 DECIMAL 或 NUMERIC 类型的浮点数精度。
  private Integer numericScale;
  // 类型处理器。负责具体执行 Java 对象与 JDBC 参数之间的转换。
  private TypeHandler<?> typeHandler;
  // 结果映射 ID。当参数类型为 ResultSet（存储过程游标）时，指定处理该结果集的 ResultMap。
  private String resultMapId;
  // JDBC 类型名称。主要用于某些特殊数据库要求的用户自定义类型（User Defined Types）。
  private String jdbcTypeName;
  // 表达式。目前 MyBatis 核心逻辑中基本不使用，留作扩展。
  private String expression;
  // 固定值。如果设置了此值，则不再从参数对象中提取，直接使用该值。
  private Object value = UNSET;

  private ParameterMapping() {
  }

  public static class Builder {
    private final ParameterMapping parameterMapping = new ParameterMapping();

    public Builder(Configuration configuration, String property, TypeHandler<?> typeHandler) {
      parameterMapping.configuration = configuration;
      parameterMapping.property = property;
      parameterMapping.typeHandler = typeHandler;
      parameterMapping.mode = ParameterMode.IN;
    }

    public Builder(Configuration configuration, String property, Class<?> javaType) {
      parameterMapping.configuration = configuration;
      parameterMapping.property = property;
      parameterMapping.javaType = javaType;
      parameterMapping.mode = ParameterMode.IN;
    }

    public Builder mode(ParameterMode mode) {
      parameterMapping.mode = mode;
      return this;
    }

    public Builder javaType(Class<?> javaType) {
      parameterMapping.javaType = javaType;
      return this;
    }

    public Builder jdbcType(JdbcType jdbcType) {
      parameterMapping.jdbcType = jdbcType;
      return this;
    }

    public Builder numericScale(Integer numericScale) {
      parameterMapping.numericScale = numericScale;
      return this;
    }

    public Builder resultMapId(String resultMapId) {
      parameterMapping.resultMapId = resultMapId;
      return this;
    }

    public Builder typeHandler(TypeHandler<?> typeHandler) {
      parameterMapping.typeHandler = typeHandler;
      return this;
    }

    public Builder jdbcTypeName(String jdbcTypeName) {
      parameterMapping.jdbcTypeName = jdbcTypeName;
      return this;
    }

    public Builder expression(String expression) {
      parameterMapping.expression = expression;
      return this;
    }

    public Builder value(Object value) {
      parameterMapping.value = value;
      return this;
    }

    public ParameterMapping build() {
      validate();
      return parameterMapping;
    }

    private void validate() {
      if (ResultSet.class.equals(parameterMapping.javaType) && parameterMapping.resultMapId == null) {
        throw new IllegalStateException("Missing resultMap in property '" + parameterMapping.property + "'.  "
            + "Parameters of type java.sql.ResultSet require a resultMap.");
      }
    }
  }

  public String getProperty() {
    return property;
  }

  /**
   * Used for handling output of callable statements.
   *
   * @return the mode
   */
  public ParameterMode getMode() {
    return mode;
  }

  /**
   * Used for handling output of callable statements.
   *
   * @return the java type
   */
  public Class<?> getJavaType() {
    return javaType;
  }

  /**
   * Used in the UnknownTypeHandler in case there is no handler for the property type.
   *
   * @return the jdbc type
   */
  public JdbcType getJdbcType() {
    return jdbcType;
  }

  /**
   * Used for handling output of callable statements.
   *
   * @return the numeric scale
   */
  public Integer getNumericScale() {
    return numericScale;
  }

  /**
   * Used when setting parameters to the PreparedStatement.
   *
   * @return the type handler
   */
  public TypeHandler<?> getTypeHandler() {
    return typeHandler;
  }

  /**
   * Used for handling output of callable statements.
   *
   * @return the result map id
   */
  public String getResultMapId() {
    return resultMapId;
  }

  /**
   * Used for handling output of callable statements.
   *
   * @return the jdbc type name
   */
  public String getJdbcTypeName() {
    return jdbcTypeName;
  }

  /**
   * Expression 'Not used'.
   *
   * @return the expression
   */
  public String getExpression() {
    return expression;
  }

  public Object getValue() {
    return value;
  }

  public boolean hasValue() {
    return value != UNSET;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("ParameterMapping{");
    // sb.append("configuration=").append(configuration); // configuration doesn't have a useful .toString()
    sb.append("property='").append(property).append('\'');
    sb.append(", mode=").append(mode);
    sb.append(", javaType=").append(javaType);
    sb.append(", jdbcType=").append(jdbcType);
    sb.append(", numericScale=").append(numericScale);
    // sb.append(", typeHandler=").append(typeHandler); // typeHandler also doesn't have a useful .toString()
    sb.append(", resultMapId='").append(resultMapId).append('\'');
    sb.append(", jdbcTypeName='").append(jdbcTypeName).append('\'');
    sb.append(", expression='").append(expression).append('\'');
    sb.append(", value='").append(value).append('\'');
    sb.append('}');
    return sb.toString();
  }
}
