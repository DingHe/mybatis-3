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
package org.apache.ibatis.type;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @author Clinton Begin
 */
// 专门负责处理 Java 中的 Short 类型与数据库中小型整数（如 SMALLINT）之间的转换。
// 其核心作用是抹平 Java 类型 Short 与 JDBC 接口 setShort/getShort 之间的差异。
// 特别需要注意的是，在 JDBC 中，getShort 方法如果遇到数据库里的 NULL 值，会返回 Java 基本类型的 0。
// ShortTypeHandler 的关键任务之一就是正确识别这个 0 到底是数据库里的数字 0 还是 NULL，从而返回给 Java 正确的对象（0 或 null）
public class ShortTypeHandler extends BaseTypeHandler<Short> {
  public static final ShortTypeHandler INSTANCE = new ShortTypeHandler();

  // 将非空的 Short 参数设置到 SQL 预编译语句中
  @Override
  public void setNonNullParameter(PreparedStatement ps, int i, Short parameter, JdbcType jdbcType) throws SQLException {
    ps.setShort(i, parameter);
  }

  // 通过列名从结果集中获取 Short 值
  @Override
  public Short getNullableResult(ResultSet rs, String columnName) throws SQLException {
    short result = rs.getShort(columnName);
    return result == 0 && rs.wasNull() ? null : result;
  }

  @Override
  public Short getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    short result = rs.getShort(columnIndex);
    return result == 0 && rs.wasNull() ? null : result;
  }

  @Override
  public Short getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
    short result = cs.getShort(columnIndex);
    return result == 0 && cs.wasNull() ? null : result;
  }
}
