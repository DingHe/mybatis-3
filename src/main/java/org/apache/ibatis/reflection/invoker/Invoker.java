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
package org.apache.ibatis.reflection.invoker;

import java.lang.reflect.InvocationTargetException;

/**
 * @author Clinton Begin
 */
// 核心思想是：屏蔽“调用”的具体细节，提供统一的访问入口。
// Invoker 的主要作用是统一对类属性、方法或字段的访问方式。
// 在 Java 中，获取或设置一个对象的值有多种方式：可能是通过 Field（字段）直接访问，也可能是通过 Method（Getter/Setter 方法）访问。
// MyBatis 为了在处理结果集映射或参数绑定时不需要判断“我是该调方法还是该点字段”，将这些行为抽象成了 Invoker。
public interface Invoker {
  // 在目标对象上执行具体的调用（获取值、设置值或执行方法）。
  // target：要操作的目标对象实例。
  // args：调用时需要的参数。如果是 Getter 或者是获取字段值，该参数通常为 null 或空数组；如果是 Setter，则包含要设置的值。
  // 返回值：执行结果。例如 Getter 会返回属性值，Setter 通常返回 null
  Object invoke(Object target, Object[] args) throws IllegalAccessException, InvocationTargetException;

  // 获取该 Invoker 操作的数据类型。
  // 如果是 MethodInvoker 包装的一个 Getter 方法，则返回该方法的返回值类型。
  // 如果是 MethodInvoker 包装的一个 Setter 方法，则返回该方法的第一个参数类型。
  // 如果是 FieldInvoker，则返回该字段的类型。
  // 应用场景：MyBatis 利用这个方法来确定需要使用哪个 TypeHandler（类型处理器）来处理数据库字段与 Java 类型之间的转换。
  Class<?> getType();
}
