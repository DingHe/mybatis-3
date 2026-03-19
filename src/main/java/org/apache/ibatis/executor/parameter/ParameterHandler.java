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
package org.apache.ibatis.executor.parameter;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * A parameter handler sets the parameters of the {@code PreparedStatement}.
 *
 * @author Clinton Begin
 */
// 负责将用户传入的 Java 参数转换为 JDBC 能够识别的 SQL 参数。
// 在 MyBatis 执行 SQL 之前，SqlSource 会生成带有 ? 占位符的 SQL 语句。ParameterHandler 的作用就是：
// 参数映射：将 Java 对象（Map、实体类、基础类型）中的属性值与 SQL 中的 ? 一一对应。
// 类型转换：调用相应的 TypeHandler，将 Java 类型（如 String）转换成 JDBC 类型（如 VARCHAR），并调用 PreparedStatement.setXXX() 方法。
public interface ParameterHandler {
  // 获取用户传入的原始参数对象。
  // 这个对象可能是你在 Mapper 接口中传入的单个参数（如 User 对象）
  // 如果你传入了多个参数，MyBatis 会将其包装成一个 MapperMethod.ParamMap（本质是一个 HashMap）
  Object getParameterObject();

  // 核心方法，
  // 负责为 PreparedStatement 中的所有 ? 占位符设置实际的值
  void setParameters(PreparedStatement ps) throws SQLException;

}
