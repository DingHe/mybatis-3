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
package org.apache.ibatis.mapping;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.property.PropertyTokenizer;
import org.apache.ibatis.session.Configuration;

/**
 * An actual SQL String got from an {@link SqlSource} after having processed any dynamic content. The SQL may have SQL
 * placeholders "?" and a list (ordered) of a parameter mappings with the additional information for each parameter (at
 * least the property name of the input object to read the value from).
 * <p>
 * Can also have additional parameters that are created by the dynamic language (for loops, bind...).
 *
 * @author Clinton Begin
 */
// 在 MyBatis 的执行流程中，BoundSql 是一个极其关键的中间产物。它是动态 SQL 解析完成后的最终成果，代表了“准备好交给 JDBC 执行的 SQL 信息”。
// BoundSql 的核心作用是：封装已经解析完成、可直接用于预编译执行的 SQL 语句及其关联的参数信息。
// 在 MyBatis 中，原始的 SQL（可能包含 <if>, <foreach> 等动态标签）通过 SqlSource 解析后，会生成 BoundSql。它是连接“动态 SQL 逻辑”与“JDBC 执行逻辑”的桥梁。它主要包含三部分：
// 静态 SQL：动态标签已被替换为 JDBC 占位符 ?。
// 参数映射列表：记录了每一个 ? 应该对应 Java 对象中的哪个属性。
// 参数上下文：包含了用户传入的实参，以及在解析过程中动态生成的临时参数（如 foreach 循环中的变量）。
public class BoundSql {
  // 最终执行的 SQL 文本。所有的 MyBatis 动态标签已被处理，参数占位符 #{} 已被替换为标准的 JDBC 问号 ?。
  private final String sql;
  // 参数映射列表。这是一个有序列表，其顺序与 SQL 中的 ? 一一对应。每个映射对象记录了该位置参数的属性名、类型处理器等。
  private final List<ParameterMapping> parameterMappings;
  // 运行时实参对象。用户在 Mapper 方法中传入的实际参数（可能是 POJO、Map 或单个基本类型）。
  private final Object parameterObject;
  // 附加参数映射表。用于存储在 SQL 解析过程中动态产生的变量。例如 <bind> 标签生成的变量或 foreach 内部的迭代变量。
  private final Map<String, Object> additionalParameters;
  // 附加参数的元数据对象。
  // 它是对 additionalParameters 的包装，利用 MyBatis 的反射系统实现对附加参数的快速读写。
  private final MetaObject metaParameters;

  public BoundSql(Configuration configuration, String sql, List<ParameterMapping> parameterMappings,
      Object parameterObject) {
    this.sql = sql;
    this.parameterMappings = parameterMappings;
    this.parameterObject = parameterObject;
    this.additionalParameters = new HashMap<>();
    this.metaParameters = configuration.newMetaObject(additionalParameters);
  }

  public String getSql() {
    return sql;
  }

  public List<ParameterMapping> getParameterMappings() {
    return parameterMappings;
  }

  public Object getParameterObject() {
    return parameterObject;
  }

  public boolean hasAdditionalParameter(String name) {
    String paramName = new PropertyTokenizer(name).getName();
    return additionalParameters.containsKey(paramName);
  }

  public void setAdditionalParameter(String name, Object value) {
    metaParameters.setValue(name, value);
  }

  public Object getAdditionalParameter(String name) {
    return metaParameters.getValue(name);
  }

  public Map<String, Object> getAdditionalParameters() {
    return additionalParameters;
  }
}
