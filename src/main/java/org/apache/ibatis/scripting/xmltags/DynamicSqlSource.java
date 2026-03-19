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
package org.apache.ibatis.scripting.xmltags;

import org.apache.ibatis.builder.SqlSourceBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.reflection.ParamNameResolver;
import org.apache.ibatis.session.Configuration;

/**
 * @author Clinton Begin
 */
// 在 MyBatis 的执行引擎中，DynamicSqlSource 是处理动态 SQL（即包含 <if>, <where>, <foreach> 等标签或 ${} 占位符的 SQL）的核心实现类。
// DynamicSqlSource 的主要作用是在运行时根据传入的参数对象，实时生成可执行的 SQL 语句。
// 由于动态 SQL 的最终形态取决于运行时的参数值（例如某个字段为 null 时不参与更新），因此 DynamicSqlSource 不能像 StaticSqlSource 那样在启动时就确定 SQL 结构。它负责执行以下流程：
// 节点遍历：解析并执行所有的动态标签逻辑。
// 变量替换：处理 ${} 文本替换。
// 参数映射提取：将解析后的 SQL（含有 #{}）交给 SqlSourceBuilder 转化为含有 ? 的标准 JDBC SQL。
public class DynamicSqlSource implements SqlSource {
  // MyBatis 的全局配置对象，用于获取反射工厂、类型处理器注册表等核心资源。
  private final Configuration configuration;
  // 动态 SQL 节点树的根节点。
  // 在解析 XML 映射文件时，动态 SQL 会被解析成一棵由不同 SqlNode 实现类（如 IfSqlNode, MixedSqlNode, TextSqlNode 等）组成的树。
  private final SqlNode rootSqlNode;
  // 参数名称解析器。
  // 用于处理 Mapper 接口方法中的参数（如 @Param 映射），确保在动态上下文中能准确找到参数值。
  private final ParamNameResolver paramNameResolver;

  public DynamicSqlSource(Configuration configuration, SqlNode rootSqlNode) {
    this(configuration, rootSqlNode, null);
  }

  public DynamicSqlSource(Configuration configuration, SqlNode rootSqlNode, ParamNameResolver paramNameResolver) {
    this.configuration = configuration;
    this.rootSqlNode = rootSqlNode;
    this.paramNameResolver = paramNameResolver;
  }

  @Override
  public BoundSql getBoundSql(Object parameterObject) {
    DynamicContext context = new DynamicContext(configuration, parameterObject, null, paramNameResolver, true);
    rootSqlNode.apply(context);
    String sql = context.getSql();
    SqlSource sqlSource = SqlSourceBuilder.buildSqlSource(configuration, sql, context.getParameterMappings());
    BoundSql boundSql = sqlSource.getBoundSql(parameterObject);
    context.getBindings().forEach(boundSql::setAdditionalParameter);
    return boundSql;
  }

}
