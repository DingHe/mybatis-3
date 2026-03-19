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
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.ibatis.binding.MapperMethod.ParamMap;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.parsing.TokenHandler;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.ParamNameResolver;
import org.apache.ibatis.reflection.property.PropertyTokenizer;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

// 专门负责处理 SQL 语句中的动态参数占位符 #{}
// 类的核心任务是将 SQL 脚本中的 #{} 占位符替换为 JDBC 标准的问号 ?，并在此过程中提取出参数的详细元数据（如属性名、Java 类型、JDBC 类型、类型处理器等）
// 当 MyBatis 遇到 select * from users where id = #{id} 时：
// ParameterMappingTokenHandler 会捕获到 id
// 将其转化为一个 ParameterMapping 对象存入列表
// 返回字符串 ?
// 最终 SQL 变为 select * from users where id = ?
public class ParameterMappingTokenHandler extends BaseBuilder implements TokenHandler {
  // 定义了 #{} 中允许出现的合法属性字段名（如 javaType, jdbcType 等）。
  private static final String PARAMETER_PROPERTIES = "javaType,jdbcType,mode,numericScale,resultMap,typeHandler,jdbcTypeName";
  // 存储解析出来的所有参数映射对象。这是一个有序列表，顺序与 SQL 中 ? 的顺序一致。
  private final List<ParameterMapping> parameterMappings;
  // 传入参数的 Java 类型（如 User.class）
  private final Class<?> parameterType;
  // 包装了 additionalParameters（如循环中的临时变量），用于快速访问。
  private final MetaObject metaParameters;
  // 运行时的实际参数对象
  private final Object parameterObject;
  // 标识是否存在运行时参数
  private final boolean paramExists;
  // 参数名称解析器，用于处理 Mapper 接口中多参数定义的情况。
  private final ParamNameResolver paramNameResolver;
  // 记录当前参数的泛型类型，用于精确查找 TypeHandler。
  private Type genericType = null;
  // 记录为当前参数解析出的类型处理器
  private TypeHandler<?> typeHandler = null;

  public ParameterMappingTokenHandler(List<ParameterMapping> parameterMappings, Configuration configuration,
      Object parameterObject, Class<?> parameterType, Map<String, Object> additionalParameters,
      ParamNameResolver paramNameResolver, boolean paramExists) {
    super(configuration);
    this.parameterType = parameterObject == null ? (parameterType == null ? Object.class : parameterType)
        : parameterObject.getClass();
    this.metaParameters = configuration.newMetaObject(additionalParameters);
    this.parameterObject = parameterObject;
    this.paramExists = paramExists;
    this.parameterMappings = parameterMappings;
    this.paramNameResolver = paramNameResolver;
  }

  public ParameterMappingTokenHandler(List<ParameterMapping> parameterMappings, Configuration configuration,
      Class<?> parameterType, Map<String, Object> additionalParameters, ParamNameResolver paramNameResolver) {
    super(configuration);
    this.parameterType = parameterType;
    this.metaParameters = configuration.newMetaObject(additionalParameters);
    this.parameterObject = null;
    this.paramExists = false;
    this.parameterMappings = parameterMappings;
    this.paramNameResolver = paramNameResolver;
  }

  public List<ParameterMapping> getParameterMappings() {
    return parameterMappings;
  }

  @Override
  public String handleToken(String content) {
    genericType = null;
    typeHandler = null;
    parameterMappings.add(buildParameterMapping(content));
    return "?";
  }

  // 任务是将 #{} 占位符内部的字符串解析并构建成一个完整的 ParameterMapping 对象。
  private ParameterMapping buildParameterMapping(String content) {
    // 将 content（即 #{} 里的内容）解析成键值对
    Map<String, String> propertiesMap = parseParameterMapping(content);
    // 优先拿走 property（属性名）、jdbcType 和 typeHandler。这些是构建映射关系的最关键信息
    final String property = propertiesMap.remove("property");
    final JdbcType jdbcType = resolveJdbcType(propertiesMap.remove("jdbcType"));
    final String typeHandlerAlias = propertiesMap.remove("typeHandler");

    ParameterMapping.Builder builder = new ParameterMapping.Builder(configuration, property, (Class<?>) null);
    // 处理嵌套属性（如 user.name），它会将 user 识别为当前段
    PropertyTokenizer propertyTokenizer = new PropertyTokenizer(property);
    builder.jdbcType(jdbcType);
    // MyBatis 会根据传入的参数对象类型、属性路径等，利用反射或元数据推断出这个字段在 Java 中是什么类型（String, Integer 等）。
    final Class<?> javaType = figureOutJavaType(propertiesMap, property, propertyTokenizer, jdbcType);
    builder.javaType(javaType);

    if (genericType == null) {
      genericType = javaType;
    }
    // 确定 TypeHandler
    if (typeHandler == null || typeHandlerAlias != null) {
      typeHandler = resolveTypeHandler(genericType, jdbcType, typeHandlerAlias);
    }
    builder.typeHandler(typeHandler);

    ParameterMode mode = null;
    // 遍历 propertiesMap 中剩余的配置，将其设置进构建器。如果出现了非法属性（如不在 PARAMETER_PROPERTIES 中），会抛出 BuilderException
    for (Map.Entry<String, String> entry : propertiesMap.entrySet()) {
      String name = entry.getKey();
      String value = entry.getValue();
      if ("mode".equals(name)) {
        mode = resolveParameterMode(value);
        builder.mode(mode);
      } else if ("numericScale".equals(name)) {
        builder.numericScale(Integer.valueOf(value));
      } else if ("resultMap".equals(name)) {
        builder.resultMapId(value);
      } else if ("jdbcTypeName".equals(name)) {
        builder.jdbcTypeName(value);
      } else if ("expression".equals(name)) {
        throw new BuilderException("Expression based parameters are not supported yet");
      } else {
        throw new BuilderException("An invalid property '" + name + "' was found in mapping #{" + content
            + "}.  Valid properties are " + PARAMETER_PROPERTIES);
      }
    }
    // 如果这不是一个输出参数（即不是存储过程的 OUT 参数），MyBatis 会尝试直接从实参对象中获取该属性的值。
    if (!ParameterMode.OUT.equals(mode) && paramExists) {
      if (metaParameters.hasGetter(propertyTokenizer.getName())) {
        builder.value(metaParameters.getValue(property));
      } else if (parameterObject == null) {
        builder.value(null);
      } else if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
        builder.value(parameterObject);
      } else {
        MetaObject metaObject = configuration.newMetaObject(parameterObject);
        builder.value(metaObject.getValue(property));
      }
    }
    return builder.build();
  }

  // 由于开发者在写 SQL 时不一定会在 #{} 中显式写明 javaType（如 #{age, javaType=int}），
  // MyBatis 必须能够根据当前的参数对象（parameterObject）、Mapper 接口定义的参数类型、
  // 以及 foreach 循环产生的临时变量，自动推导出对应的 Java 类型。这样才能为后续选择正确的 TypeHandler 提供依据。
  private Class<?> figureOutJavaType(Map<String, String> propertiesMap, String property,
      PropertyTokenizer propertyTokenizer, JdbcType jdbcType) {
    // 第一优先级：显式配置
    Class<?> javaType = resolveClass(propertiesMap.remove("javaType"));
    if (javaType != null) {
      return javaType;
    }
    // 第二优先级：附加参数（额外上下文）
    if (metaParameters.hasGetter(propertyTokenizer.getName())) { // issue #448 get type from additional params
      return metaParameters.getGetterType(property);
    }
    // 第三优先级：基础类型判断
    typeHandler = resolveTypeHandler(parameterType, jdbcType, (Class<? extends TypeHandler<?>>) null);
    if (typeHandler != null) {
      return parameterType;
    }
    if (JdbcType.CURSOR.equals(jdbcType)) {
      return ResultSet.class;
    }
    if (paramNameResolver != null && ParamMap.class.equals(parameterType)) {
      Type actualParamType = paramNameResolver.getType(property);
      if (actualParamType instanceof Type) {
        MetaClass metaClass = MetaClass.forClass(actualParamType, configuration.getReflectorFactory());
        String multiParamsPropertyName;
        if (propertyTokenizer.hasNext()) {
          multiParamsPropertyName = propertyTokenizer.getChildren();
          if (metaClass.hasGetter(multiParamsPropertyName)) {
            Entry<Type, Class<?>> getterType = metaClass.getGenericGetterType(multiParamsPropertyName);
            genericType = getterType.getKey();
            return getterType.getValue();
          }
        } else {
          genericType = actualParamType;
        }
      }
      return Object.class;
    }
    if (Map.class.isAssignableFrom(parameterType)) {
      return Object.class;
    }
    // 第五优先级：普通实体类反射
    MetaClass metaClass = MetaClass.forClass(parameterType, configuration.getReflectorFactory());
    if (metaClass.hasGetter(property)) {
      Entry<Type, Class<?>> getterType = metaClass.getGenericGetterType(property);
      genericType = getterType.getKey();
      return getterType.getValue();
    }
    return Object.class;
  }

  // 将 文本内容（即 #{} 里的内容）解析成键值对
  private Map<String, String> parseParameterMapping(String content) {
    try {
      return new ParameterExpression(content);
    } catch (BuilderException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new BuilderException("Parsing error was found in mapping #{" + content
          + "}.  Check syntax #{property|(expression), var1=value1, var2=value2, ...} ", ex);
    }
  }
}
