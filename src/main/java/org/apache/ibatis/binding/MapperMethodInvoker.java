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
package org.apache.ibatis.binding;

import java.lang.reflect.Method;

import org.apache.ibatis.session.SqlSession;

// 在 MyBatis 的源码中，它是为了解耦 Mapper 接口方法调用 与 底层 SQL 执行逻辑 而设计的。
// MapperMethodInvoker 的主要作用是封装 Mapper 接口方法的执行逻辑。
// 在 MyBatis 中，当你调用一个 Mapper 接口的方法（例如 userMapper.selectById(1)）时，实际上是由一个动态代理对象拦截了这个调用。代理对象需要决定：
// 这个方法是一个普通 SQL 映射方法（即在 XML 或注解中定义的方法）？
// 还是一个 Java 8 的接口默认方法（Default Method）？
// MapperMethodInvoker 就像是一个“执行适配器”。
// 对于不同类型的方法，MyBatis 会提供不同的实现类（如 PlainMethodInvoker 处理常规 SQL，DefaultMethodInvoker 处理接口默认方法）。
// 这种设计符合策略模式，使得代理对象无需关心具体的执行细节，只需调用 invoke 即可。
public interface MapperMethodInvoker {
  // 这是该接口的唯一方法，负责执行具体的调用逻辑。
  // Object proxy 当前的代理对象实例（即 MyBatis 为你生成的 Mapper 接口实现类实例）。
  // Method method: 前正在被调用的方法对象。它包含了方法的名称、返回类型、参数定义等反射信息。
  // 调用方法时传入的实际参数数组（例如 id=1）。
  // SqlSession sqlSession:当前的会话对象。它是 MyBatis 执行数据库操作的核心入口，invoke 最终会通过它来与数据库交互。
  Object invoke(Object proxy, Method method, Object[] args, SqlSession sqlSession) throws Throwable;

}
