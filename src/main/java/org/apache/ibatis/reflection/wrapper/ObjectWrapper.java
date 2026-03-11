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
package org.apache.ibatis.reflection.wrapper;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map.Entry;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.property.PropertyTokenizer;

/**
 * @author Clinton Begin
 */
// 在 MyBatis 的反射模块中，ObjectWrapper 是一个处于核心地位的适配器接口。它位于 org.apache.ibatis.reflection.wrapper 包下。
// ObjectWrapper 的核心作用是：统一对不同类型对象的访问方式。
// 在 Java 中，操作对象的方式多种多样：
// POJO (Bean)：通过 Getter/Setter 或 Field。
// Map：通过 get("key") / put("key", value)。
// Collection/Array：通过索引或 add() 方法。
// MyBatis 的 MetaObject 类在处理 SQL 参数绑定或结果集映射时，不希望处理这些复杂的判断逻辑。
// ObjectWrapper 就像一层“外壳”，将这些差异封装起来。无论底层是 Bean、Map 还是 List，上层都只需要调用统一的 get、set 等方法。
public interface ObjectWrapper {
  // 获取属性值。
  // 参数：PropertyTokenizer（属性分词器），用于处理分层属性（如 user.address.street）。
  Object get(PropertyTokenizer prop);

  // 设置属性值。
  void set(PropertyTokenizer prop, Object value);

  // 查找属性名。
  // 参数：支持是否开启下划线转驼峰的映射查找。
  String findProperty(String name, boolean useCamelCaseMapping);

  // 作用：返回该对象所有可读（Getter）或可写（Setter）的属性名数组。
  String[] getGetterNames();

  String[] getSetterNames();

  // 作用：获取指定属性的 Java 类型。
  Class<?> getSetterType(String name);

  Class<?> getGetterType(String name);

  default Entry<Type, Class<?>> getGenericSetterType(String name) {
    throw new UnsupportedOperationException(
        "'" + this.getClass() + "' must override the default method 'getGenericSetterType()'.");
  }

  default Entry<Type, Class<?>> getGenericGetterType(String name) {
    throw new UnsupportedOperationException(
        "'" + this.getClass() + "' must override the default method 'getGenericGetterType()'.");
  }

  // 作用：判断当前对象是否拥有指定属性的可读/可写能力。
  boolean hasSetter(String name);

  boolean hasGetter(String name);

  // 作用：当属性值为 null 且需要进行级联设置时，自动实例化该属性对象。
  MetaObject instantiatePropertyValue(String name, PropertyTokenizer prop, ObjectFactory objectFactory);

  // 作用：判断被包装的对象是否为集合类型。
  boolean isCollection();

  // 作用：向集合中添加单个元素。
  void add(Object element);

  // 作用：向集合中批量添加元素。
  <E> void addAll(List<E> element);

}
