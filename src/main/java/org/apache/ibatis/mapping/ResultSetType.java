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

import java.sql.ResultSet;

/**
 * @author Clinton Begin
 */
// 要用于对 JDBC 中的 java.sql.ResultSet（结果集）类型进行封装和映射。
// 在 JDBC 编程中，当你执行一个查询时，返回的 ResultSet 有不同的“滚动”和“敏感”行为。ResultSetType 的作用就是在 MyBatis 配置中统一定义这些行为。
// 简单来说，它决定了你在获取数据库结果后：
// 游标（Cursor）是否可以上下自由移动（滚动）。
// 当数据库中的原始数据发生变化时，已经打开的结果集是否能感知到这种变化。
public enum ResultSetType {
  /**
   * behavior with same as unset (driver dependent).
   *
   * @since 3.5.0
   */
  // 默认行为。表示不显式设置结果集类型。此时的行为完全取决于底层数据库驱动（Driver）的默认实现。通常大多数驱动默认都是 FORWARD_ONLY。
  DEFAULT(-1),
  // 只进不退。游标只能向前移动（从第一行到最后一行）。这是性能最高、最常用的类型，因为它不需要数据库服务器或驱动程序缓存大量数据。
  FORWARD_ONLY(ResultSet.TYPE_FORWARD_ONLY),
  // 不敏感的可滚动结果集。游标可以前后移动，甚至移动到绝对位置。
  // 但它是“不敏感”的，即在结果集打开期间，如果别人修改了数据库里的数据，这个结果集里的数据不会更新。
  SCROLL_INSENSITIVE(ResultSet.TYPE_SCROLL_INSENSITIVE),
  // 敏感的可滚动结果集。游标可以自由前后移动。
  // 它是“敏感”的，如果数据库中的数据发生了变化，结果集通常能感知并反映出这些变化（取决于驱动支持）。
  SCROLL_SENSITIVE(ResultSet.TYPE_SCROLL_SENSITIVE);

  // 存储该枚举项对应的整数值。
  // 这个值通常对应于 java.sql.ResultSet 接口中定义的常量字段（如 1003 代表 TYPE_FORWARD_ONLY），MyBatis 在底层设置 JDBC Statement 时会用到这个整数。
  private final int value;

  ResultSetType(int value) {
    this.value = value;
  }

  public int getValue() {
    return value;
  }
}
