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
package org.apache.ibatis.reflection;

import java.lang.reflect.Type;

// ReflectorFactory 是一个工厂接口。它定义了如何创建和管理 Reflector 对象的标准。
// 解耦 Reflector 对象的创建逻辑，并提供缓存管理能力。
// 由于 Reflector 在实例化时会通过反射执行大量的类解析操作（扫描方法、字段、处理冲突等），这是一个非常昂贵的 CPU 消耗过程。因此，框架需要一个工厂来统一管理：
// 单例控制：确保同一个类不会被重复解析。
// 缓存调度：通过开关控制是否缓存解析结果。
// 扩展性：允许用户自定义反射行为（虽然 99% 的场景下使用默认实现 DefaultReflectorFactory 即可）。
public interface ReflectorFactory {
  // 查询当前工厂是否启用了类缓存功能。
  boolean isClassCacheEnabled();

  // 设置是否开启类缓存。
  void setClassCacheEnabled(boolean classCacheEnabled);

  // 根据传入的类型（Type 或 Class）获取对应的 Reflector 实例。
  // type：可以是普通的 Class 对象，也可以是带泛型信息的 ParameterizedType。
  Reflector findForClass(Type type);
}
