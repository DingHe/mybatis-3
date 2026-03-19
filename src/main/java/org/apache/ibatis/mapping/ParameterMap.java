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

import java.util.Collections;
import java.util.List;

import org.apache.ibatis.session.Configuration;

/**
 * @author Clinton Begin
 */
// 主要用于定义 SQL 语句执行时参数的映射规则。
// ParameterMap 的主要作用是描述 SQL 语句输入参数的结构。
// 它对应于 MyBatis XML 映射文件中早期使用的 <parameterMap> 标签（尽管该标签在现代 MyBatis 开发中已不推荐使用，转而推荐直接在 SQL 中使用 #{}），
// 但在 MyBatis 内部引擎中，所有的参数处理逻辑最终都会被封装成一个 ParameterMap 对象。
public class ParameterMap {
  // 该参数映射表的唯一标识符。通常与 SQL 语句（MappedStatement）的 ID 相关联。
  private String id;
  // 输入参数的 Java 类型（例如 User.class 或 Map.class）。它决定了 MyBatis 应该从什么类型的对象中提取数据。
  private Class<?> type;
  // 一组有序的参数映射列表。每一个 ParameterMapping 对应 SQL 中的一个占位符 ?，记录了该位置参数的属性名、JDBC 类型、TypeHandler 等详细信息。
  private List<ParameterMapping> parameterMappings;

  private ParameterMap() {
  }

  public static ParameterMap buildEmpty(String statementId, Class<?> parameterType) {
    ParameterMap emptyParameterMap = new ParameterMap();
    emptyParameterMap.id = statementId;
    emptyParameterMap.type = parameterType;
    emptyParameterMap.parameterMappings = Collections.emptyList();
    return emptyParameterMap;
  }

  public static class Builder {
    private final ParameterMap parameterMap = new ParameterMap();

    public Builder(Configuration configuration, String id, Class<?> type, List<ParameterMapping> parameterMappings) {
      parameterMap.id = id;
      parameterMap.type = type;
      parameterMap.parameterMappings = parameterMappings;
    }

    public Class<?> type() {
      return parameterMap.type;
    }

    public ParameterMap build() {
      // lock down collections
      parameterMap.parameterMappings = Collections.unmodifiableList(parameterMap.parameterMappings);
      return parameterMap;
    }
  }

  public String getId() {
    return id;
  }

  public Class<?> getType() {
    return type;
  }

  public List<ParameterMapping> getParameterMappings() {
    return parameterMappings;
  }

}
