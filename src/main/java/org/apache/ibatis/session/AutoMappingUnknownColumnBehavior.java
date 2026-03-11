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
package org.apache.ibatis.session;

import java.lang.reflect.Type;

import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.mapping.MappedStatement;

/**
 * Specify the behavior when detects an unknown column (or unknown property type) of automatic mapping target.
 *
 * @since 3.4.0
 *
 * @author Kazuki Shimizu
 */
// AutoMappingUnknownColumnBehavior 是 MyBatis 3.4.0 版本引入的一个枚举类。它与 AutoMappingBehavior 配合使用，专门负责处理自动映射过程中出现的“异常情况”。
// 该类的主要作用是：指定当 MyBatis 探测到无法处理的自动映射列（或未知属性类型）时的行为策略。
// 在自动映射过程中，可能会遇到以下两种棘手情况：
// 找不到匹配属性：SQL 查询返回了一个列，但在目标 Java 对象中找不到对应的属性名。
// 缺少类型处理器：找到了对应的属性，但该属性的类型没有注册相应的 TypeHandler（导致无法完成转换）。
// 通过这个类，你可以决定当这些情况发生时，是“假装没看见”、“打印警告日志”还是“直接让程序报错”。
public enum AutoMappingUnknownColumnBehavior {

  /**
   * Do nothing (Default).
   */
  // 默认行为
  // 什么都不做。这是最常用的模式，MyBatis 会静默忽略掉那些无法映射的列。
  NONE {
    @Override
    public void doAction(MappedStatement mappedStatement, String columnName, String property, Type propertyType) {
      // do nothing
    }
  },

  /**
   * Output warning log. Note: The log level of {@code 'org.apache.ibatis.session.AutoMappingUnknownColumnBehavior'}
   * must be set to {@code WARN}.
   */
  // 警告模式
  // 打印一条警告（WARN）级别的日志。你需要确保日志配置中开启了对该类的 WARN 级别监控。
  WARNING {
    @Override
    public void doAction(MappedStatement mappedStatement, String columnName, String property, Type propertyType) {
      LogHolder.log.warn(buildMessage(mappedStatement, columnName, property, propertyType));
    }
  },

  /**
   * Fail mapping. Note: throw {@link SqlSessionException}.
   */
  // 失败模式
  // 直接抛出 SqlSessionException。这是一种“防御式编程”策略，强制开发者必须处理所有返回的列，确保映射百分之百精确。
  FAILING {
    @Override
    public void doAction(MappedStatement mappedStatement, String columnName, String property, Type propertyType) {
      throw new SqlSessionException(buildMessage(mappedStatement, columnName, property, propertyType));
    }
  };

  /**
   * Perform the action when detects an unknown column (or unknown property type) of automatic mapping target.
   *
   * @param mappedStatement
   *          current mapped statement
   * @param columnName
   *          column name for mapping target
   * @param propertyName
   *          property name for mapping target
   * @param propertyType
   *          property type for mapping target (If this argument is not null, {@link org.apache.ibatis.type.TypeHandler}
   *          for property type is not registered)
   */
  public abstract void doAction(MappedStatement mappedStatement, String columnName, String propertyName,
      Type propertyType);

  /**
   * build error message.
   */
  private static String buildMessage(MappedStatement mappedStatement, String columnName, String property,
      Type propertyType) {
    return new StringBuilder("Unknown column is detected on '").append(mappedStatement.getId())
        .append("' auto-mapping. Mapping parameters are ").append("[").append("columnName=").append(columnName)
        .append(",").append("propertyName=").append(property).append(",").append("propertyType=")
        .append(propertyType != null ? propertyType.getTypeName() : null).append("]").toString();
  }

  private static class LogHolder {
    private static final Log log = LogFactory.getLog(AutoMappingUnknownColumnBehavior.class);
  }

}
