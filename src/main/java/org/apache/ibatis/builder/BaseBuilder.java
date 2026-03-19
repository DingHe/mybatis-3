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
package org.apache.ibatis.builder;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.ResultSetType;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeAliasRegistry;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;

/**
 * @author Clinton Begin
 */
// BaseBuilder 是 MyBatis 框架中所有“构建器”类的基类。在 MyBatis 解析配置文件（如 mybatis-config.xml）或映射文件（如
// Mapper.xml）的过程中，它提供了一套通用的工具方法和共享的资源引用。
// BaseBuilder 采用了模板模式或作为工具基类设计，其主要作用包括：
// 资源共享：为子类统一提供 Configuration（全局配置）、TypeAliasRegistry（别名注册器）和 TypeHandlerRegistry（类型处理器注册器）的引用。
// 类型解析：提供将配置文件中的字符串（String）转换为 Java 实际对象（Class、Enum、TypeHandler 等）的便捷方法。
// 容错处理：封装了基础的类型转换逻辑，并统一抛出 BuilderException，简化了子类的异常处理过程。

public abstract class BaseBuilder {
  // MyBatis 的全局配置对象，包含了所有的环境设置、插件、映射器等核心信息。
  protected final Configuration configuration;
  // 别名注册器。子类通过它将配置文件里的短名称（如 int）解析为实际的 Class 对象。
  protected final TypeAliasRegistry typeAliasRegistry;
  // 类型处理器注册器。用于查找和管理 Java 类型与 JDBC 类型之间的转换逻辑。
  protected final TypeHandlerRegistry typeHandlerRegistry;

  public BaseBuilder(Configuration configuration) {
    this.configuration = configuration;
    this.typeAliasRegistry = this.configuration.getTypeAliasRegistry();
    this.typeHandlerRegistry = this.configuration.getTypeHandlerRegistry();
  }

  public Configuration getConfiguration() {
    return configuration;
  }

  // 将给定的正则表达式字符串编译为 Pattern 对象。如果字符串为空，则使用默认值。
  protected Pattern parseExpression(String regex, String defaultValue) {
    return Pattern.compile(regex == null ? defaultValue : regex);
  }

  // 安全地将字符串转为 Boolean。处理 null 值并返回默认值。
  protected Boolean booleanValueOf(String value, Boolean defaultValue) {
    return value == null ? defaultValue : Boolean.valueOf(value);
  }

  // 安全地将字符串转为 Integer
  protected Integer integerValueOf(String value, Integer defaultValue) {
    return value == null ? defaultValue : Integer.valueOf(value);
  }

  // 将逗号分隔的字符串（如 "a,b,c"）转换为 HashSet 集合。
  protected Set<String> stringSetValueOf(String value, String defaultValue) {
    value = value == null ? defaultValue : value;
    return new HashSet<>(Arrays.asList(value.split(",")));
  }

  // 将字符串转换为 JdbcType 枚举（如 "VARCHAR" 转为 JdbcType.VARCHAR）
  protected JdbcType resolveJdbcType(String alias) {
    try {
      return alias == null ? null : JdbcType.valueOf(alias);
    } catch (IllegalArgumentException e) {
      throw new BuilderException("Error resolving JdbcType. Cause: " + e, e);
    }
  }

  // 解析结果集滚动类型（如 FORWARD_ONLY）
  protected ResultSetType resolveResultSetType(String alias) {
    try {
      return alias == null ? null : ResultSetType.valueOf(alias);
    } catch (IllegalArgumentException e) {
      throw new BuilderException("Error resolving ResultSetType. Cause: " + e, e);
    }
  }

  // 解析参数模式（IN, OUT, INOUT）
  protected ParameterMode resolveParameterMode(String alias) {
    try {
      return alias == null ? null : ParameterMode.valueOf(alias);
    } catch (IllegalArgumentException e) {
      throw new BuilderException("Error resolving ParameterMode. Cause: " + e, e);
    }
  }

  // 根据别名或全类名，通过反射创建一个对象的实例（调用无参构造函数）
  protected Object createInstance(String alias) {
    Class<?> clazz = resolveClass(alias);
    try {
      return clazz == null ? null : clazz.getDeclaredConstructor().newInstance();
    } catch (Exception e) {
      throw new BuilderException("Error creating instance. Cause: " + e, e);
    }
  }

  // 将别名或类名字符串解析为 Class 对象
  protected <T> Class<? extends T> resolveClass(String alias) {
    try {
      return alias == null ? null : resolveAlias(alias);
    } catch (Exception e) {
      throw new BuilderException("Error resolving class. Cause: " + e, e);
    }
  }

  @Deprecated(since = "3.6.0", forRemoval = true)
  protected TypeHandler<?> resolveTypeHandler(Class<?> javaType, String typeHandlerAlias) {
    return resolveTypeHandler(javaType, null, typeHandlerAlias);
  }

  // 早期版本用于解析类型处理器
  @Deprecated(since = "3.6.0", forRemoval = true)
  protected TypeHandler<?> resolveTypeHandler(Class<?> javaType, Class<? extends TypeHandler<?>> typeHandlerType) {
    return resolveTypeHandler(javaType, null, typeHandlerType);
  }

  // 早期版本用于通过处理器类解析实例
  protected TypeHandler<?> resolveTypeHandler(Type propertyType, JdbcType jdbcType, String typeHandlerAlias) {
    Class<? extends TypeHandler<?>> typeHandlerType = null;
    typeHandlerType = resolveClass(typeHandlerAlias);
    if (typeHandlerType != null && !TypeHandler.class.isAssignableFrom(typeHandlerType)) {
      throw new BuilderException("Type " + typeHandlerType.getName()
          + " is not a valid TypeHandler because it does not implement TypeHandler interface");
    }
    return resolveTypeHandler(propertyType, jdbcType, typeHandlerType);
  }

  // 根据属性类型、JDBC 类型和处理器的别名来解析并获取 TypeHandler 实例
  protected TypeHandler<?> resolveTypeHandler(Type javaType, JdbcType jdbcType,
      Class<? extends TypeHandler<?>> typeHandlerType) {
    if (typeHandlerType == null && jdbcType == null) {
      return null;
    }
    return configuration.getTypeHandlerRegistry().getTypeHandler(javaType, jdbcType, typeHandlerType);
  }

  protected <T> Class<? extends T> resolveAlias(String alias) {
    return typeAliasRegistry.resolveAlias(alias);
  }
}
