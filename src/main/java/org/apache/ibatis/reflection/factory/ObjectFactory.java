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
package org.apache.ibatis.reflection.factory;

import java.util.List;
import java.util.Properties;

/**
 * MyBatis uses an ObjectFactory to create all needed new Objects.
 *
 * @author Clinton Begin
 */
// 在 MyBatis 的架构中，ObjectFactory（对象工厂）是一个非常关键的扩展点。它负责 MyBatis 运行过程中所有实例对象的创建工作。
// 对象实例化控制中心。
// 统一对象创建：无论是查询结果映射生成的 POJO，还是 MyBatis 内部使用的集合类（如 List、Map），甚至是参数对象，都是通过 ObjectFactory 创建的。
// 提供扩展钩子：开发者可以实现该接口并配置到 MyBatis 中，从而在对象被实例化之后、返回给框架之前进行特殊处理。
// 例如：给所有实体类自动注入某种全局 ID，或者通过依赖注入框架（如 Spring/Guice）来托管对象的生命周期。
public interface ObjectFactory {

  /**
   * Sets configuration properties.
   *
   * @param properties
   *          configuration properties
   */
  // 初始化工厂配置。
  // MyBatis 会将配置文件（如 mybatis-config.xml）中 <objectFactory> 标签下定义的 <property> 子标签内容通过此方法传递进来。
  default void setProperties(Properties properties) {
    // NOP
  }

  /**
   * Creates a new object with default constructor.
   *
   * @param <T>
   *          the generic type
   * @param type
   *          Object type
   *
   * @return the t
   */
  // 使用默认无参构造函数创建一个指定类型的对象。
  // type：需要实例化的 Java 类（Class 对象）。
  <T> T create(Class<T> type);

  /**
   * Creates a new object with the specified constructor and params.
   *
   * @param <T>
   *          the generic type
   * @param type
   *          Object type
   * @param constructorArgTypes
   *          Constructor argument types
   * @param constructorArgs
   *          Constructor argument values
   *
   * @return the t
   */
  // 使用指定的构造函数及其参数创建一个新对象。
  // type：目标对象的类型。
  // constructorArgTypes：构造函数参数的类型列表（用于定位具体的重载构造函数）。
  // constructorArgs：构造函数执行时传入的实际参数值列表。
  <T> T create(Class<T> type, List<Class<?>> constructorArgTypes, List<Object> constructorArgs);

  /**
   * Returns true if this object can have a set of other objects. It's main purpose is to support
   * non-java.util.Collection objects like Scala collections.
   *
   * @param <T>
   *          the generic type
   * @param type
   *          Object type
   *
   * @return whether it is a collection or not
   *
   * @since 3.1.0
   */
  // 作用：判断指定的类型是否为集合。
  // 返回值：如果是集合类型（如 List、Set 等）则返回 true，否则返回 false。
  // 设计目的：主要为了支持非 java.util.Collection 的集合，例如 Scala 的集合类。
  // MyBatis 需要知道这个类是否可以容纳一组其他对象，以便正确地处理一对多（Collection）的映射逻辑。
  <T> boolean isCollection(Class<T> type);

}
