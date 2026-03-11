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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.ReflectPermission;
import java.lang.reflect.Type;
import java.text.MessageFormat;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.ibatis.reflection.invoker.AmbiguousMethodInvoker;
import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.invoker.SetFieldInvoker;
import org.apache.ibatis.reflection.property.PropertyNamer;

/**
 * This class represents a cached set of class definition information that allows for easy mapping between property
 * names and getter/setter methods.
 *
 * @author Clinton Begin
 */
// 在 MyBatis 的反射模块中，Reflector 类是绝对的核心。它相当于一个**“类元数据缓存库”**。
// Reflector 的核心作用是解析并缓存 Java 类的定义信息。
// 为了避免在运行期间反复使用昂贵的 Java 反射操作，MyBatis 会为每个需要处理的类创建一个 Reflector 实例。
// 它通过解析类的 Getter、Setter 方法和字段，将它们映射为简单的属性名，并封装成 Invoker 对象。
// 属性与方法解耦：开发者只需要提供属性名（如 "username"），Reflector 就能自动找到对应的 getField、setField 或 getMethods。
// 解决命名冲突：处理 Java 继承体系中同名方法的冲突，确保映射的准确性。
// 适配特殊类型：支持对 Java 14+ 中 Record 类型的自动探测与处理。
public class Reflector {
  // 用于兼容检测 Java Record 类型的句柄。
  private static final MethodHandle isRecordMethodHandle = getIsRecordMethodHandle();
  // 正在被解析的原始类型。
  private final Type type;
  // 正在被解析的类的 Class 对象。
  private final Class<?> clazz;
  // 可读属性（有 Getter 或公开字段）的名称数组。
  private final String[] readablePropertyNames;
  // 可写属性（有 Setter 或公开字段）的名称数组。
  private final String[] writablePropertyNames;
  // 属性名到 Setter 执行器的映射。
  private final Map<String, Invoker> setMethods = new HashMap<>();
  // 属性名到 Getter 执行器的映射。
  private final Map<String, Invoker> getMethods = new HashMap<>();
  // 属性名到 Setter 参数类型的映射（包含泛型信息）。
  private final Map<String, Entry<Type, Class<?>>> setTypes = new HashMap<>();
  // 属性名到 Getter 返回类型的映射（包含泛型信息）。
  private final Map<String, Entry<Type, Class<?>>> getTypes = new HashMap<>();
  // 类的默认无参构造函数。
  private Constructor<?> defaultConstructor;
  // 大小写不敏感的属性映射表（Key 全部转为大写）。
  private final Map<String, String> caseInsensitivePropertyMap = new HashMap<>();

  private static final Entry<Type, Class<?>> nullEntry = new AbstractMap.SimpleImmutableEntry<>(null, null);

  // 核心构造函数
  // 在创建一个 Reflector 实例时，立即对传入的 Java 类型进行“深度扫描”，并将该类所有的属性、方法、构造函数等信息解析并缓存起来。
  public Reflector(Type type) {
    this.type = type;
    // 如果是泛型类型（如 List<User>），获取其原始类（如 List.class）
    // 作用：将复杂的 Type 对象（可能包含泛型信息）转换为可操作的 Class 对象。
    if (type instanceof ParameterizedType) {
      this.clazz = (Class<?>) ((ParameterizedType) type).getRawType();
    } else {
      this.clazz = (Class<?>) type;
    }
    // 查找并存储该类的无参构造函数，以便 MyBatis 之后能通过 newInstance() 创建该类的实例。
    addDefaultConstructor(clazz);
    // 获取所有方法
    // 会向上遍历整个继承树（包括接口），收集所有声明的方法。
    Method[] classMethods = getClassMethods(clazz);
    if (isRecord(clazz)) {
      // 针对 Java 14+ 的 Record 类型
      addRecordGetMethods(classMethods);
    } else {
      // // 针对标准的普通 POJO/JavaBean
      addGetMethods(classMethods); // 解析 getXXX 和 isXXX 方法
      addSetMethods(classMethods); // 解析 setXXX 方法
      addFields(clazz); // 扫描 Field，处理没有 getter/setter 的属性
    }
    // 将解析出来的所有可读、可写属性名分别存入数组，方便后续快速遍历。
    readablePropertyNames = getMethods.keySet().toArray(new String[0]);
    writablePropertyNames = setMethods.keySet().toArray(new String[0]);
    for (String propName : readablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
    for (String propName : writablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
  }

  private void addRecordGetMethods(Method[] methods) {
    Arrays.stream(methods).filter(m -> m.getParameterTypes().length == 0)
        .forEach(m -> addGetMethod(m.getName(), m, false));
  }

  // 从目标类中寻找一个“无参构造函数”（Default Constructor），并将其缓存到 defaultConstructor 属性中。
  private void addDefaultConstructor(Class<?> clazz) {
    Constructor<?>[] constructors = clazz.getDeclaredConstructors();
    Arrays.stream(constructors).filter(constructor -> constructor.getParameterTypes().length == 0).findAny()
        .ifPresent(constructor -> this.defaultConstructor = constructor);
  }

  // 从一组方法中筛选出符合 JavaBean 规范的 Getter 方法，并处理同名属性对应多个 Getter 的冲突情况。
  // 在 Java 继承体系中，同一个属性名可能会对应多个方法（例如父类返回 List，子类重写后返回 ArrayList）。该方法先将这些同名属性的方法归类，然后交给 resolveGetterConflicts 去裁决谁才是真正的
  // Getter。
  private void addGetMethods(Method[] methods) {
    // Key: 属性名（如 username）。
    // Value: 该属性对应的所有候选 Getter 方法列表。
    Map<String, List<Method>> conflictingGetters = new HashMap<>();
    // Getter 必须是无参的。
    // 方法名必须以 get 或 is 开头（且后续字符符合规范）。
    // 将方法名转换为属性名（例如：将 getName 或 isAlive 转换为 name 或 alive）。
    Arrays.stream(methods).filter(m -> m.getParameterTypes().length == 0 && PropertyNamer.isGetter(m.getName()))
        .forEach(m -> addMethodConflict(conflictingGetters, PropertyNamer.methodToProperty(m.getName()), m));
    // 解决冲突
    // 将所有分类好的“疑似 Getter”送入冲突解决器。在那里，MyBatis 会根据返回值的继承关系、方法名的微小差异（如 is vs get）来选出最合适的一个。
    resolveGetterConflicts(conflictingGetters);
  }

  // 负责在众多的候选方法中，“裁决”出哪一个才是最合适的 Getter。
  // 处理同名属性对应的多个 Getter 方法冲突，选出唯一的“胜出者”（Winner）。
  // 在 Java 继承或接口实现中，同一个属性（如 name）可能会对应多个方法（如 getName() 返回 Object 和 getName() 返回 String）。该方法通过一套严密的比较规则，确保 MyBatis
  // 映射到最具体的实现方法。
  private void resolveGetterConflicts(Map<String, List<Method>> conflictingGetters) {
    for (Entry<String, List<Method>> entry : conflictingGetters.entrySet()) {
      Method winner = null;
      String propName = entry.getKey();
      boolean isAmbiguous = false;
      for (Method candidate : entry.getValue()) {
        if (winner == null) {
          winner = candidate;
          continue;
        }
        Class<?> winnerType = winner.getReturnType();
        Class<?> candidateType = candidate.getReturnType();
        // 规则一：类型相同时的抉择
        if (candidateType.equals(winnerType)) {
          // 非布尔类型：如果两个方法返回类型相同但不是 boolean，MyBatis 认为这违反了 JavaBean 规范（存在歧义），标记为 isAmbiguous = true。
          if (!boolean.class.equals(candidateType)) {
            isAmbiguous = true;
            break;
          }
          // 布尔类型：如果返回类型都是 boolean，则优先选择以 is 开头的方法（例如 isAlive() 优于 getAlive()）。
          if (candidate.getName().startsWith("is")) {
            winner = candidate;
          }
          // 规则二：存在父子类继承关系（协变返回类型）

        } else if (candidateType.isAssignableFrom(winnerType)) {
          // OK getter type is descendant
          // 如果 candidate 的返回类型是 winner 返回类型的子类， 则 candidate 胜出。
        } else if (winnerType.isAssignableFrom(candidateType)) {
          winner = candidate;
        } else {
          isAmbiguous = true;
          break;
        }
      }
      addGetMethod(propName, winner, isAmbiguous);
    }
  }

  // 为属性创建执行器（Invoker），并解析该属性对应的最终数据类型。
  private void addGetMethod(String name, Method method, boolean isAmbiguous) {
    // 创建执行器 (Invoker)
    MethodInvoker invoker = isAmbiguous ? new AmbiguousMethodInvoker(method, MessageFormat.format(
        "Illegal overloaded getter method with ambiguous type for property ''{0}'' in class ''{1}''. This breaks the JavaBeans specification and can cause unpredictable results.",
        name, method.getDeclaringClass().getName())) : new MethodInvoker(method);
    getMethods.put(name, invoker);
    Type returnType = TypeParameterResolver.resolveReturnType(method, type);
    getTypes.put(name, Map.entry(returnType, typeToClass(returnType)));
  }

  // 从类的方法集合中提取出符合 JavaBean 规范的 Setter 方法，并将同名属性的多个 Setter 方法收集在一起，准备进行冲突解析。
  // 在 Java 中，Setter 方法的复杂性高于 Getter，因为一个属性可能存在方法重载（Overload）（例如：setAge(int) 和
  // setAge(String)）。该方法先将这些候选者按属性名归类，后续由专门的逻辑决定哪一个是“真命天子”。
  private void addSetMethods(Method[] methods) {
    Map<String, List<Method>> conflictingSetters = new HashMap<>();
    Arrays.stream(methods).filter(m -> m.getParameterTypes().length == 1 && PropertyNamer.isSetter(m.getName()))
        .forEach(m -> addMethodConflict(conflictingSetters, PropertyNamer.methodToProperty(m.getName()), m));
    resolveSetterConflicts(conflictingSetters);
  }

  private void addMethodConflict(Map<String, List<Method>> conflictingMethods, String name, Method method) {
    if (isValidPropertyName(name)) {
      List<Method> list = conflictingMethods.computeIfAbsent(name, k -> new ArrayList<>());
      list.add(method);
    }
  }

  private void resolveSetterConflicts(Map<String, List<Method>> conflictingSetters) {
    for (Entry<String, List<Method>> entry : conflictingSetters.entrySet()) {
      String propName = entry.getKey();
      List<Method> setters = entry.getValue();
      Class<?> getterType = getTypes.getOrDefault(propName, nullEntry).getValue();
      boolean isGetterAmbiguous = getMethods.get(propName) instanceof AmbiguousMethodInvoker;
      boolean isSetterAmbiguous = false;
      Method match = null;
      for (Method setter : setters) {
        if (!isGetterAmbiguous && setter.getParameterTypes()[0].equals(getterType)) {
          // should be the best match
          match = setter;
          break;
        }
        if (!isSetterAmbiguous) {
          match = pickBetterSetter(match, setter, propName);
          isSetterAmbiguous = match == null;
        }
      }
      if (match != null) {
        addSetMethod(propName, match);
      }
    }
  }

  private Method pickBetterSetter(Method setter1, Method setter2, String property) {
    if (setter1 == null) {
      return setter2;
    }
    Class<?> paramType1 = setter1.getParameterTypes()[0];
    Class<?> paramType2 = setter2.getParameterTypes()[0];
    if (paramType1.isAssignableFrom(paramType2)) {
      return setter2;
    }
    if (paramType2.isAssignableFrom(paramType1)) {
      return setter1;
    }
    MethodInvoker invoker = new AmbiguousMethodInvoker(setter1,
        MessageFormat.format(
            "Ambiguous setters defined for property ''{0}'' in class ''{1}'' with types ''{2}'' and ''{3}''.", property,
            setter2.getDeclaringClass().getName(), paramType1.getName(), paramType2.getName()));
    setMethods.put(property, invoker);
    Type[] paramTypes = TypeParameterResolver.resolveParamTypes(setter1, type);
    setTypes.put(property, Map.entry(paramTypes[0], typeToClass(paramTypes[0])));
    return null;
  }

  private void addSetMethod(String name, Method method) {
    MethodInvoker invoker = new MethodInvoker(method);
    setMethods.put(name, invoker);
    Type[] paramTypes = TypeParameterResolver.resolveParamTypes(method, type);
    setTypes.put(name, Map.entry(paramTypes[0], typeToClass(paramTypes[0])));
  }

  private Class<?> typeToClass(Type src) {
    if (src instanceof Class) {
      return (Class<?>) src;
    } else if (src instanceof ParameterizedType) {
      return (Class<?>) ((ParameterizedType) src).getRawType();
    } else if (src instanceof GenericArrayType) {
      Type componentType = ((GenericArrayType) src).getGenericComponentType();
      if (componentType instanceof Class) {
        return Array.newInstance((Class<?>) componentType, 0).getClass();
      } else {
        Class<?> componentClass = typeToClass(componentType);
        return Array.newInstance(componentClass, 0).getClass();
      }
    }
    return Object.class;
  }

  private void addFields(Class<?> clazz) {
    Field[] fields = clazz.getDeclaredFields();
    for (Field field : fields) {
      if (!setMethods.containsKey(field.getName())) {
        // issue #379 - removed the check for final because JDK 1.5 allows
        // modification of final fields through reflection (JSR-133). (JGB)
        // pr #16 - final static can only be set by the classloader
        int modifiers = field.getModifiers();
        if (!Modifier.isFinal(modifiers) || !Modifier.isStatic(modifiers)) {
          addSetField(field);
        }
      }
      if (!getMethods.containsKey(field.getName())) {
        addGetField(field);
      }
    }
    if (clazz.getSuperclass() != null) {
      addFields(clazz.getSuperclass());
    }
  }

  private void addSetField(Field field) {
    if (isValidPropertyName(field.getName())) {
      setMethods.put(field.getName(), new SetFieldInvoker(field));
      Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
      setTypes.put(field.getName(), Map.entry(fieldType, typeToClass(fieldType)));
    }
  }

  private void addGetField(Field field) {
    if (isValidPropertyName(field.getName())) {
      getMethods.put(field.getName(), new GetFieldInvoker(field));
      Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
      getTypes.put(field.getName(), Map.entry(fieldType, typeToClass(fieldType)));
    }
  }

  private boolean isValidPropertyName(String name) {
    return !name.startsWith("$") && !"serialVersionUID".equals(name) && !"class".equals(name);
  }

  /**
   * This method returns an array containing all methods declared in this class and any superclass. We use this method,
   * instead of the simpler <code>Class.getMethods()</code>, because we want to look for private methods as well.
   *
   * @param clazz
   *          The class
   *
   * @return An array containing all methods in this class
   */
  // 负责全量收集一个类及其继承体系中定义的所有方法。
  // 主要职责是：打破 Java 标准 API 的限制，获取一个类及其所有父类、所有实现的接口中声明的所有方法（包括私有方法）。
  private Method[] getClassMethods(Class<?> clazz) {
    // Key 是方法的唯一签名（由 getSignature 生成），Value 是 Method 对象。
    // 这样可以自动处理方法重写（Override）的情况：子类的方法会覆盖父类的同名同参数方法。
    Map<String, Method> uniqueMethods = new HashMap<>();
    Class<?> currentClass = clazz;
    while (currentClass != null && currentClass != Object.class) {
      // 1. 获取当前类自己声明的所有方法（含私有）
      addUniqueMethods(uniqueMethods, currentClass.getDeclaredMethods());

      // we also need to look for interface methods -
      // because the class may be abstract
      // 原因：如果当前类是一个 abstract 类，它可能没有实现接口中的方法。为了保证元数据的完整性，必须显式地去扫描它所实现的接口。
      // 这里使用 anInterface.getMethods() 是因为接口方法默认都是 public 的。
      Class<?>[] interfaces = currentClass.getInterfaces();
      for (Class<?> anInterface : interfaces) {
        addUniqueMethods(uniqueMethods, anInterface.getMethods());
      }
      // 循环处理父类
      currentClass = currentClass.getSuperclass();
    }

    Collection<Method> methods = uniqueMethods.values();

    return methods.toArray(new Method[0]);
  }

  // 将一组方法加入到已有的方法集合中，同时剔除“桥接方法”并处理“方法重写”。
  // addUniqueMethods 保证了：
  // 如果子类重写了父类的方法，Map 中存储的将是子类的方法。
  // 屏蔽掉编译器自动生成的辅助方法（桥接方法）。
  private void addUniqueMethods(Map<String, Method> uniqueMethods, Method[] methods) {
    for (Method currentMethod : methods) {
      // 什么是桥接方法？ 当类实现泛型接口或继承泛型父类时，Java 编译器为了保持字节码的兼容性，会自动生成一些“桥接方法”。
      // 为什么要过滤？ 桥接方法通常只是对真实方法的简单包装，且签名往往与真实方法冲突。MyBatis 只需要开发者编写的那个真实方法。
      if (!currentMethod.isBridge()) {
        String signature = getSignature(currentMethod);
        // check to see if the method is already known
        // if it is known, then an extended class must have
        // overridden a method
        if (!uniqueMethods.containsKey(signature)) {
          uniqueMethods.put(signature, currentMethod);
        }
      }
    }
  }

  private String getSignature(Method method) {
    StringBuilder sb = new StringBuilder();
    Class<?> returnType = method.getReturnType();
    sb.append(returnType.getName()).append('#');
    sb.append(method.getName());
    Class<?>[] parameters = method.getParameterTypes();
    for (int i = 0; i < parameters.length; i++) {
      sb.append(i == 0 ? ':' : ',').append(parameters[i].getName());
    }
    return sb.toString();
  }

  /**
   * Checks whether can control member accessible.
   *
   * @return If can control member accessible, it return {@literal true}
   *
   * @since 3.5.0
   */
  public static boolean canControlMemberAccessible() {
    try {
      SecurityManager securityManager = System.getSecurityManager();
      if (null != securityManager) {
        securityManager.checkPermission(new ReflectPermission("suppressAccessChecks"));
      }
    } catch (SecurityException e) {
      return false;
    }
    return true;
  }

  /**
   * Gets the name of the class the instance provides information for.
   *
   * @return The class name
   */
  public Class<?> getType() {
    return clazz;
  }

  public Constructor<?> getDefaultConstructor() {
    if (defaultConstructor != null) {
      return defaultConstructor;
    }
    throw new ReflectionException("There is no default constructor for " + clazz);
  }

  public boolean hasDefaultConstructor() {
    return defaultConstructor != null;
  }

  public Invoker getSetInvoker(String propertyName) {
    Invoker method = setMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + clazz + "'");
    }
    return method;
  }

  public Invoker getGetInvoker(String propertyName) {
    Invoker method = getMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + clazz + "'");
    }
    return method;
  }

  /**
   * Gets the type for a property setter.
   *
   * @param propertyName
   *          - the name of the property
   *
   * @return The Class of the property setter
   */
  public Class<?> getSetterType(String propertyName) {
    Class<?> clazz = setTypes.get(propertyName).getValue();
    if (clazz == null) {
      throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + clazz + "'");
    }
    return clazz;
  }

  public Entry<Type, Class<?>> getGenericSetterType(String propertyName) {
    return setTypes.computeIfAbsent(propertyName, k -> {
      throw new ReflectionException("There is no setter for property named '" + k + "' in '" + clazz + "'");
    });
  }

  /**
   * Gets the type for a property getter.
   *
   * @param propertyName
   *          - the name of the property
   *
   * @return The Class of the property getter
   */
  public Class<?> getGetterType(String propertyName) {
    Class<?> clazz = getTypes.getOrDefault(propertyName, nullEntry).getValue();
    if (clazz == null) {
      throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + clazz + "'");
    }
    return clazz;
  }

  public Entry<Type, Class<?>> getGenericGetterType(String propertyName) {
    return getTypes.computeIfAbsent(propertyName, k -> {
      throw new ReflectionException("There is no getter for property named '" + k + "' in '" + clazz + "'");
    });
  }

  /**
   * Gets an array of the readable properties for an object.
   *
   * @return The array
   */
  public String[] getGetablePropertyNames() {
    return readablePropertyNames;
  }

  /**
   * Gets an array of the writable properties for an object.
   *
   * @return The array
   */
  public String[] getSetablePropertyNames() {
    return writablePropertyNames;
  }

  /**
   * Check to see if a class has a writable property by name.
   *
   * @param propertyName
   *          - the name of the property to check
   *
   * @return True if the object has a writable property by the name
   */
  public boolean hasSetter(String propertyName) {
    return setMethods.containsKey(propertyName);
  }

  /**
   * Check to see if a class has a readable property by name.
   *
   * @param propertyName
   *          - the name of the property to check
   *
   * @return True if the object has a readable property by the name
   */
  public boolean hasGetter(String propertyName) {
    return getMethods.containsKey(propertyName);
  }

  public String findPropertyName(String name) {
    return caseInsensitivePropertyMap.get(name.toUpperCase(Locale.ENGLISH));
  }

  /**
   * Class.isRecord() alternative for Java 15 and older.
   */
  private static boolean isRecord(Class<?> clazz) {
    try {
      return isRecordMethodHandle != null && (boolean) isRecordMethodHandle.invokeExact(clazz);
    } catch (Throwable e) {
      throw new ReflectionException("Failed to invoke 'Class.isRecord()'.", e);
    }
  }

  private static MethodHandle getIsRecordMethodHandle() {
    MethodHandles.Lookup lookup = MethodHandles.lookup();
    MethodType mt = MethodType.methodType(boolean.class);
    try {
      return lookup.findVirtual(Class.class, "isRecord", mt);
    } catch (NoSuchMethodException | IllegalAccessException e) {
      return null;
    }
  }
}
