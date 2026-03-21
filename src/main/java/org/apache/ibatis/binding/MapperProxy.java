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

import java.io.Serializable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

import org.apache.ibatis.reflection.ExceptionUtil;
import org.apache.ibatis.session.SqlSession;

/**
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
// Gemini said
// MapperProxy 是 MyBatis 核心架构中极其关键的一个类。它是 MyBatis 能够实现“只需定义接口，无需编写实现类即可执行 SQL”这一神奇特性的幕后功臣。
// MapperProxy 实现了 JDK 的 InvocationHandler 接口，它是 Mapper 接口的动态代理逻辑处理器。
// 在 MyBatis 中，当你通过 sqlSession.getMapper(UserMapper.class) 获取一个接口实例时，返回的其实是一个被 MapperProxy 劫持的代理对象。其核心作用包括：
// 拦截方法调用：拦截所有对 Mapper 接口的方法调用。
// 分发执行逻辑：区分对待 Object 原生方法（如 toString）、Java 8 接口默认方法（default method）以及普通的 SQL 映射方法。
// 方法缓存优化：为了避免重复解析方法注解和 XML 配置，它内部维护了一个缓存，提高执行效率。
public class MapperProxy<T> implements InvocationHandler, Serializable {

  private static final long serialVersionUID = -4724728412955527868L;
  // 存储 Java 9 引入的 MethodHandles.privateLookupIn 方法，用于处理接口默认方法的访问控制。
  private static final Method privateLookupInMethod;
  // MyBatis 的核心会话。代理对象最终通过它来执行数据库的增删改查操作。
  private final SqlSession sqlSession;
  // 当前代理的 Mapper 接口类型（例如 UserMapper.class）。
  private final Class<T> mapperInterface;
  // 方法缓存。Key 是接口的方法对象，Value 是封装了执行逻辑的调用器。这是提升性能的关键。
  private final Map<Method, MapperMethodInvoker> methodCache;

  public MapperProxy(SqlSession sqlSession, Class<T> mapperInterface, Map<Method, MapperMethodInvoker> methodCache) {
    this.sqlSession = sqlSession;
    this.mapperInterface = mapperInterface;
    this.methodCache = methodCache;
  }

  static {
    try {
      privateLookupInMethod = MethodHandles.class.getMethod("privateLookupIn", Class.class, MethodHandles.Lookup.class);
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException(
          "There is no 'privateLookupIn(Class, Lookup)' method in java.lang.invoke.MethodHandles.", e);
    }
  }

  // 核心入口方法，也是 JDK 动态代理规范中 InvocationHandler 接口的要求。
  // 每当你调用 Mapper 接口的一个方法时，程序都会“掉进”这个 invoke 方法里。
  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    try {
      // 如果用户调用的是 userMapper.toString()，我们不需要去数据库查 SQL，而是直接让 MapperProxy 实例（即 this）执行其自带的 toString()
      // 逻辑。这保证了代理对象在调试、日志记录时的基本行为正常。
      if (Object.class.equals(method.getDeclaringClass())) {
        return method.invoke(this, args);
      }
      // 缓存与执行
      // 拿到具体的执行器（Invoker）后，把所有的上下文（代理对象、参数、SqlSession）都丢给它。
      return cachedInvoker(method).invoke(proxy, method, args, sqlSession);
    } catch (Throwable t) {
      throw ExceptionUtil.unwrapThrowable(t);
    }
  }

  // 核心任务是：针对接口中的每一个方法，只进行一次“深度扫描”，然后将执行策略缓存起来。
  private MapperMethodInvoker cachedInvoker(Method method) throws Throwable {
    try {
      return methodCache.computeIfAbsent(method, m -> {
        // 判定：如果这不是一个 Java 8 的 default 接口方法。
        if (!m.isDefault()) {
          // 处理：创建一个 MapperMethod 对象。
          // 这个对象非常重，它会去解析 XML 或注解里的 SQL、输入参数映射和输出结果映射。
          // 包装：将其封装在 PlainMethodInvoker 中，后续调用时直接执行 SQL。
          return new PlainMethodInvoker(new MapperMethod(mapperInterface, method, sqlSession.getConfiguration()));
        }
        try {
          // 调用 getMethodHandleJava9。它不走 MyBatis 的 SQL 引擎，而是通过 Java 底层的 MethodHandle（方法句柄）技术来获取对该默认方法实现的直接引用。
          return new DefaultMethodInvoker(getMethodHandleJava9(method));
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
          throw new RuntimeException(e);
        }
      });
    } catch (RuntimeException re) {
      Throwable cause = re.getCause();
      throw cause == null ? re : cause;
    }
  }

  private MethodHandle getMethodHandleJava9(Method method)
      throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
    final Class<?> declaringClass = method.getDeclaringClass();
    return ((Lookup) privateLookupInMethod.invoke(null, declaringClass, MethodHandles.lookup())).findSpecial(
        declaringClass, method.getName(), MethodType.methodType(method.getReturnType(), method.getParameterTypes()),
        declaringClass);
  }

  // 名字 “Plain” 意指“普通”或“常规”，专门负责处理那些定义在 XML 或注解中的标准 SQL 映射方法。
  // 其唯一使命是：持有并触发 MapperMethod 的执行。
  // 在 MyBatis 中，MapperMethod 是一个非常“重”的对象，它包含了 SQL 的类型（INSERT, UPDATE, SELECT 等）、参数转换规则以及结果映射逻辑。
  // PlainMethodInvoker 将这个复杂的对象包装起来，使得 MapperProxy 可以用统一的方式调用所有类型的方法。
  private static class PlainMethodInvoker implements MapperMethodInvoker {
    // 存储该接口方法对应的 SQL 执行元数据。
    private final MapperMethod mapperMethod;

    public PlainMethodInvoker(MapperMethod mapperMethod) {
      this.mapperMethod = mapperMethod;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args, SqlSession sqlSession) throws Throwable {
      return mapperMethod.execute(sqlSession, args);
    }
  }

  private static class DefaultMethodInvoker implements MapperMethodInvoker {
    private final MethodHandle methodHandle;

    public DefaultMethodInvoker(MethodHandle methodHandle) {
      this.methodHandle = methodHandle;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args, SqlSession sqlSession) throws Throwable {
      return methodHandle.bindTo(proxy).invokeWithArguments(args);
    }
  }
}
