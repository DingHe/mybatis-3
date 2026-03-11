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
package org.apache.ibatis.session;

/**
 * Specifies if and how MyBatis should automatically map columns to fields/properties.
 *
 * @author Eduardo Macarron
 */
// 定义 MyBatis 在处理结果集（ResultSet）时，自动将数据库列名映射到 Java 对象属性的激进程度。
// 在 MyBatis 中，我们通常使用 resultMap 来手动指定列与属性的对应关系。但为了省事，MyBatis 提供了“自动映射”功能：只要列名（或别名）和属性名匹配（忽略大小写或下划线转驼峰），它就能自动填充对象。
// AutoMappingBehavior 就是用来控制这种自动化行为的边界，特别是在处理涉及“关联查询”（如 association 或 collection）的复杂映射时，防止产生意外的赋值。
public enum AutoMappingBehavior {

  /**
   * Disables auto-mapping.
   */
  // 禁用自动映射。
  // MyBatis 不会自动尝试填充任何属性。只有在 resultMap 中显式通过 <id> 或 <result> 标签配置过的属性才会被赋值。
  NONE,

  /**
   * Will only auto-map results with no nested result mappings defined inside.
   */
  // 部分自动映射（MyBatis 的默认值）。
  // 对于没有定义嵌套结果映射（Nested Result Mappings）的语句，会自动映射所有匹配的列。
  // 关键点：如果 resultMap 中使用了嵌套（例如定义了级联对象 association），MyBatis 为了安全起见，将停止对该映射块的自动处理，仅映射你显式指定的列。
  // 适用场景：大多数常规开发场景。它在便利性和安全性之间取得了平衡，避免了在复杂嵌套对象中因列名重复而导致的错误赋值。
  PARTIAL,

  /**
   * Will auto-map result mappings of any complexity (containing nested or otherwise).
   */
  // 全量自动映射。
  // 无论 resultMap 的结构多么复杂（包含多少层嵌套），MyBatis 都会尝试自动映射每一层中匹配的列。
  // 风险点：如果主表和关联表中有相同的列名（例如都有 id 或 name），且没有在 SQL 中使用别名区分，可能会导致数据错误地填充到不该填的对象属性中。
  // 适用场景：SQL 语句编写非常规范（所有列名都唯一或带有明确别名），且希望最大化利用自动映射减少代码量的场景。
  FULL
}
