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

/**
 * Represents the content of a mapped statement read from an XML file or an annotation. It creates the SQL that will be
 * passed to the database out of the input parameter received from the user.
 *
 * @author Clinton Begin
 */
// 在 MyBatis 的架构设计中，SqlSource 是动态 SQL 处理的核心接口。它位于 org.apache.ibatis.mapping 包下，是连接“配置定义”与“实际执行”的关键环节。
// SqlSource 的核心作用是：根据用户传入的参数对象，动态生成可执行的 SQL 语句。
// 在 MyBatis 中，我们在 XML 或注解中定义的 SQL 可能包含各种动态元素（如 <if>, <foreach>, #{} 等）。这些原始定义在加载时会被封装成 SqlSource 对象。
// 当程序运行时，SqlSource 负责执行以下逻辑：
// 逻辑处理：解析动态标签，根据参数判断哪些 SQL 片段需要保留，哪些需要剔除。
// 变量替换：处理 SQL 中的 #{} 占位符。
// 产出结果：最终生成一个 BoundSql 对象，这个对象包含了 JDBC 直接可用的 ? 占位符 SQL 字符串以及对应的参数映射。
public interface SqlSource {
  // 作用：获取与当前执行上下文绑定的 BoundSql 实例。
  // parameterObject：这是用户在调用 Mapper 接口方法时传入的实际参数（实参）。
  // 它可以是一个简单的基本类型（如 Integer）、一个 POJO 对象、一个 Map，或者是多个参数组成的 ParamMap。
  BoundSql getBoundSql(Object parameterObject);

}
