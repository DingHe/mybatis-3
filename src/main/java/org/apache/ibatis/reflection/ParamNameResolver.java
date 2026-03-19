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

import java.lang.annotation.Annotation;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.binding.MapperMethod.ParamMap;
import org.apache.ibatis.reflection.property.PropertyTokenizer;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

// 在 MyBatis 的执行体系中，ParamNameResolver 是一个非常关键的工具类。
// 它主要负责将 Mapper 接口方法中的形参（Parameters）转换为 SQL 语句中可以识别的参数名（Names）。
// 该类的核心职责是：建立一套参数索引与参数名称之间的映射规则。
// 当我们在 Mapper 接口中定义一个方法时，如 findUser(int id, String name)，MyBatis 在执行 SQL 前需要知道如何通过 #{id} 或 #{name} 找到对应的实参。ParamNameResolver 就在初始化阶段解析这个方法的签名，确定：
// 是否使用了 @Param 注解。
// 如果没用注解，是使用 Java 8 的真实变量名，还是使用默认的 arg0, arg1 或 param1, param2。
// 如何剔除像 RowBounds 和 ResultHandler 这种不参与 SQL 传参的特殊参数。
public class ParamNameResolver {
  // 静态常量，通用参数名的前缀 "param"。
  public static final String GENERIC_NAME_PREFIX = "param";
  // 静态缓存，预存了 param1 到 param10，避免重复拼接字符串。
  public static final String[] GENERIC_NAME_CACHE = new String[10];

  static {
    for (int i = 0; i < 10; i++) {
      GENERIC_NAME_CACHE[i] = GENERIC_NAME_PREFIX + (i + 1);
    }
  }
  // 是否使用 Java 8 反射获取的真实参数名（由配置 useActualParamName 决定）。
  private final boolean useActualParamName;

  /**
   * The key is the index and the value is the name of the parameter.<br />
   * The name is obtained from {@link Param} if specified. When {@link Param} is not specified, the parameter index is
   * used. Note that this index could be different from the actual index when the method has special parameters (i.e.
   * {@link RowBounds} or {@link ResultHandler}).
   * <ul>
   * <li>aMethod(@Param("M") int a, @Param("N") int b) -&gt; {{0, "M"}, {1, "N"}}</li>
   * <li>aMethod(int a, int b) -&gt; {{0, "0"}, {1, "1"}}</li>
   * <li>aMethod(int a, RowBounds rb, int b) -&gt; {{0, "0"}, {2, "1"}}</li>
   * </ul>
   */
  // 核心属性。Key 是参数在方法中的索引（0, 1...），Value 是解析出的参数名。
  private final SortedMap<Integer, String> names;
  // 参数名与参数类型的映射表，用于后续类型校验和处理。
  private final Map<String, Type> typeMap = new HashMap<>();
  // 标记该方法中是否有任何一个参数使用了 @Param 注解。
  private boolean hasParamAnnotation;
  // 标记是否需要将参数封装进 ParamMap（即 Map 结构）中。
  private boolean useParamMap;

  // MyBatis 启动并解析 Mapper 接口时运行，决定了你在 XML 中通过什么名字（如 #{id} 或 #{arg0}）来引用 Java 方法的参数
  // 主要任务是遍历方法的所有参数，过滤掉无效参数（如分页插件使用的 RowBounds），并为剩下的每个参数确定一个“唯一标识符”（Key）。
  public ParamNameResolver(Configuration config, Method method, Class<?> mapperClass) {
    // 读取全局配置：是否使用编译后的真实变量名（对应 mybatis-config.xml 中的 useActualParamName）
    this.useActualParamName = config.isUseActualParamName();
    // 通过反射获取方法的所有参数类型数组
    final Class<?>[] paramTypes = method.getParameterTypes();
    // 获取方法的所有参数注解（二维数组，因为一个参数可以有多个注解）
    final Annotation[][] paramAnnotations = method.getParameterAnnotations();
    /// 创建一个 TreeMap 来存储参数索引到参数名称的映射，保证顺序
    final SortedMap<Integer, String> map = new TreeMap<>();
    // 解析泛型信息（处理继承或接口中的泛型，确保能拿到真实的参数类型）
    Type[] actualParamTypes = TypeParameterResolver.resolveParamTypes(method, mapperClass);
    int paramCount = paramAnnotations.length;
    // get names from @Param annotations
    for (int paramIndex = 0; paramIndex < paramCount; paramIndex++) {
      // 1. 过滤特殊参数：RowBounds 和 ResultHandler。
      // 这两类参数 MyBatis 会特殊处理，不会被当作 SQL 运行参数，因此直接跳过。
      if (isSpecialParameter(paramTypes[paramIndex])) {
        // skip special parameters
        continue;
      }
      String name = null;
      // 2. 尝试从 @Param 注解中获取名称
      for (Annotation annotation : paramAnnotations[paramIndex]) {
        if (annotation instanceof Param) {
          hasParamAnnotation = true;
          useParamMap = true;
          name = ((Param) annotation).value();
          break;
        }
      }
      // 3. 如果没有 @Param 注解，进入兜底逻辑
      if (name == null) {
        // @Param was not specified.
        // 情况 A：配置了使用真实参数名 (Java 8+ 编译时带了 -parameters 参数)
        if (useActualParamName) {
          name = getActualParamName(method, paramIndex);
        }
        // 情况 B：如果依然获取不到名称，则使用索引作为名称（如 "0", "1"...）
        if (name == null) {
          // use the parameter index as the name ("0", "1", ...)
          // gcode issue #71
          name = String.valueOf(map.size());
        }
      }
      map.put(paramIndex, name);
      typeMap.put(name, actualParamTypes[paramIndex]);
    }
    names = Collections.unmodifiableSortedMap(map);
    // 如果参数个数大于 1，强制开启 useParamMap
    // 这意味着在 SQL 中除了用名称，还可以用 param1, param2... 这种方式访问
    if (names.size() > 1) {
      useParamMap = true;
    }
    // // 特殊逻辑：如果只有一个参数，且该参数是集合或数组，MyBatis 会默认添加特定 Key
    if (names.size() == 1) {
      Type soleParamType = actualParamTypes[0];
      // 如果是泛型数组，存入 "array"
      if (soleParamType instanceof GenericArrayType) {
        typeMap.put("array", soleParamType);
      } else {
        Class<?> soleParamClass = null;
        // 获取单参数的 Class 类型
        if (soleParamType instanceof ParameterizedType) {
          soleParamClass = (Class<?>) ((ParameterizedType) soleParamType).getRawType();
        } else if (soleParamType instanceof Class) {
          soleParamClass = (Class<?>) soleParamType;
        }
        // 如果是集合类型（Collection），存入 "collection"
        if (Collection.class.isAssignableFrom(soleParamClass)) {
          typeMap.put("collection", soleParamType);
          if (List.class.isAssignableFrom(soleParamClass)) {
            typeMap.put("list", soleParamType);
          }
        }
      }
    }
  }

  // 获取 Java 编译后的真实变量名
  private String getActualParamName(Method method, int paramIndex) {
    return ParamNameUtil.getParamNames(method).get(paramIndex);
  }

  // 定义哪些参数是“特殊”的，不应该被解析为 SQL 映射参数。
  // RowBounds：用于逻辑分页。MyBatis 拦截到这个参数后，会在内存或物理分页中直接使用它，而不是把它传给 SQL 去填充 #{...}。
  // ResultHandler：用于自定义结果集处理器。当你需要自己处理每一行查询结果（而不是让 MyBatis 返回 List）时使用。
  private static boolean isSpecialParameter(Class<?> clazz) {
    return RowBounds.class.isAssignableFrom(clazz) || ResultHandler.class.isAssignableFrom(clazz);
  }

  /**
   * Returns parameter names referenced by SQL providers.
   *
   * @return the names
   */
  // 假设你有一个接口方法：
  // void selectUser(@Param("id") int id, RowBounds rb, @Param("name") String name);
  // 构造函数执行后：names 的内容是 {0: "id", 2: "name"}（注意索引 1 的 RowBounds 被过滤掉了）
  public String[] getNames() {
    return names.values().toArray(new String[0]);
  }

  /**
   * A single non-special parameter is returned without a name. Multiple parameters are named using the naming rule. In
   * addition to the default names, this method also adds the generic names (param1, param2, ...).
   *
   * @param args
   *          the args
   *
   * @return the named params
   */
  // 作用是将用户调用 Mapper 方法时传入的实参（Object[] args），转换成 MyBatis 能够识别的 参数 Map。
  public Object getNamedParams(Object[] args) {
    final int paramCount = names.size();
    // 如果没有参数，或者 names 映射表为空（排除了 RowBounds 等之后），直接返回 null
    if (args == null || paramCount == 0) {
      return null;
    }
    // 如果没有 @Param 注解，且有效参数只有一个
    if (!hasParamAnnotation && paramCount == 1) {
      // // 1. 根据 names 中唯一的 key（索引），从实参数组 args 中取出该参数值
      // Object value = args[names.firstKey()];
      // // 2. 调用 wrapToMapIfCollection 方法
      // // 如果这个参数是 Collection 或数组，它会被包装成一个 Map（Key 为 "collection", "list" 或 "array"）
      // // 这样你才能在 XML 中使用 <foreach collection="list">
      Object value = args[names.firstKey()];
      return wrapToMapIfCollection(value, useActualParamName ? names.get(names.firstKey()) : null);
    } else {
      // 当参数不止一个，或者用户明确使用了 @Param 时，MyBatis 必须返回一个 Map。
      final Map<String, Object> param = new ParamMap<>();
      int i = 0;
      for (Map.Entry<Integer, String> entry : names.entrySet()) {
        param.put(entry.getValue(), args[entry.getKey()]);
        // add generic param names (param1, param2, ...)
        final String genericParamName = i < 10 ? GENERIC_NAME_CACHE[i] : GENERIC_NAME_PREFIX + (i + 1);
        // ensure not to overwrite parameter named with @Param
        if (!names.containsValue(genericParamName)) {
          param.put(genericParamName, args[entry.getKey()]);
        }
        i++;
      }
      return param;
    }
  }

  // 根据参数名称获取其对应的 Java 类型（Type）。这在 MyBatis 处理结果映射、类型转换（TypeHandler）以及验证参数合法性时非常重要。
  public Type getType(String name) {
    // 1. 使用 PropertyTokenizer 解析传入的名称。
    // 如果传入 "users[0]"，getName() 会得到 "users"，getIndex() 会得到 "0"。
    PropertyTokenizer propertyTokenizer = new PropertyTokenizer(name);
    String unindexed = propertyTokenizer.getName();
    // 2. 尝试直接从 typeMap 中获取类型（typeMap 是在构造函数中填充好的）
    Type type = typeMap.get(unindexed);
    // // 如果找不到类型，且名称以 "param" 开头
    if (type == null && unindexed.startsWith(GENERIC_NAME_PREFIX)) {
      try {
        // 1. 截取数字部分。例如 "param1" 截取出来是 1。
        // 2. 减 1 得到索引：param1 对应数组/集合中的 index 0。
        Integer paramIndex = Integer.valueOf(unindexed.substring(GENERIC_NAME_PREFIX.length())) - 1;
        unindexed = names.get(paramIndex);
        if (unindexed != null) {
          type = typeMap.get(unindexed);
        }
      } catch (NumberFormatException e) {
        // user mistake
      }
    }
    // 如果解析结果中包含索引（说明是数组、List 或 Map）
    if (propertyTokenizer.getIndex() != null) {
      // 情况 A：参数是参数化类型（如 List<User>）
      if (type instanceof ParameterizedType) {
        Type[] typeArgs = ((ParameterizedType) type).getActualTypeArguments();
        return typeArgs[0];
        // 情况 B：参数是普通数组（如 User[]）
      } else if (type instanceof Class && ((Class<?>) type).isArray()) {
        return ((Class<?>) type).getComponentType();
      }
    }
    return type;
  }

  /**
   * Wrap to a {@link ParamMap} if object is {@link Collection} or array.
   *
   * @param object
   *          a parameter object
   * @param actualParamName
   *          an actual parameter name (If specify a name, set an object to {@link ParamMap} with specified name)
   *
   * @return a {@link ParamMap}
   *
   * @since 3.5.5
   */
  public static Object wrapToMapIfCollection(Object object, String actualParamName) {
    if (object instanceof Collection) {
      ParamMap<Object> map = new ParamMap<>();
      map.put("collection", object);
      if (object instanceof List) {
        map.put("list", object);
      }
      Optional.ofNullable(actualParamName).ifPresent(name -> map.put(name, object));
      return map;
    }
    if (object != null && object.getClass().isArray()) {
      ParamMap<Object> map = new ParamMap<>();
      map.put("array", object);
      Optional.ofNullable(actualParamName).ifPresent(name -> map.put(name, object));
      return map;
    }
    return object;
  }

  public boolean isUseParamMap() {
    return useParamMap;
  }
}
