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

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.ibatis.annotations.Flush;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.ParamNameResolver;
import org.apache.ibatis.reflection.TypeParameterResolver;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSession;

/**
 * @author Clinton Begin
 * @author Eduardo Macarron
 * @author Lasse Voss
 * @author Kazuki Shimizu
 */
// MapperMethod 是 MyBatis 框架中连接 Java 接口方法与 SQL 语句执行的关键类。如果说 MapperProxy 是“接线员”，那么 MapperMethod 就是具体的“执行官”。
// MapperMethod 的主要作用是：封装并执行 Mapper 接口中定义的方法逻辑。
// 它在初始化时会解析接口方法的参数、返回类型以及对应的 SQL 语句（MappedStatement），并在运行时根据这些元数据，调用 SqlSession 相应的方法（如 insert, update, selectList
// 等）来完成数据库操作。
public class MapperMethod {
  // 存储 SQL 命令的名称（Statement ID）和类型（INSERT/UPDATE 等）
  private final SqlCommand command;
  // 存储 Java 接口方法的签名信息。
  private final MethodSignature method;

  public MapperMethod(Class<?> mapperInterface, Method method, Configuration config) {
    this.command = new SqlCommand(config, mapperInterface, method);
    this.method = new MethodSignature(config, mapperInterface, method);
  }

  // 核心职责是：根据 SQL 命令类型和方法的返回签名，决定调用 SqlSession 的哪个底层方法，并确保返回的数据类型与接口定义完全匹配。
  public Object execute(SqlSession sqlSession, Object[] args) {
    Object result;
    switch (command.getType()) {
      case INSERT: {
        // 利用 ParamNameResolver 将用户传入的参数数组（如 [1, "Tom"]）转换成 MyBatis 能识别的参数对象（如 Map {id: 1, name: "Tom"}）
        // 注意 rowCountResult 方法。由于 JDBC 返回的是 int（受影响行数），但 Mapper 接口可能定义返回 boolean（是否成功）或 Long，该方法负责执行这个类型转换。
        Object param = method.convertArgsToSqlCommandParam(args);
        result = rowCountResult(sqlSession.insert(command.getName(), param));
        break;
      }
      case UPDATE: {
        Object param = method.convertArgsToSqlCommandParam(args);
        result = rowCountResult(sqlSession.update(command.getName(), param));
        break;
      }
      case DELETE: {
        Object param = method.convertArgsToSqlCommandParam(args);
        result = rowCountResult(sqlSession.delete(command.getName(), param));
        break;
      }
      case SELECT:
        // 如果方法返回 void 且参数里有 ResultHandler，调用 select 方法。这通常用于大数据量处理，结果通过回调函数处理，不直接返回。
        if (method.returnsVoid() && method.hasResultHandler()) {
          executeWithResultHandler(sqlSession, args);
          result = null;
          // 返回多个结果（Many）：
        } else if (method.returnsMany()) {
          result = executeForMany(sqlSession, args);
        } else if (method.returnsMap()) {
          result = executeForMap(sqlSession, args);
        } else if (method.returnsCursor()) {
          result = executeForCursor(sqlSession, args);
        } else {
          Object param = method.convertArgsToSqlCommandParam(args);
          result = sqlSession.selectOne(command.getName(), param);
          if (method.returnsOptional() && (result == null || !method.getReturnType().equals(result.getClass()))) {
            result = Optional.ofNullable(result);
          }
        }
        break;
      case FLUSH:
        result = sqlSession.flushStatements();
        break;
      default:
        throw new BindingException("Unknown execution method for: " + command.getName());
    }
    if (result == null && method.getReturnType().isPrimitive() && !method.returnsVoid()) {
      throw new BindingException("Mapper method '" + command.getName()
          + "' attempted to return null from a method with a primitive return type (" + method.getReturnType() + ").");
    }
    return result;
  }

  // 在 MyBatis 执行完 INSERT、UPDATE 或 DELETE 语句后，底层 JDBC 驱动会返回一个 int 值，表示受影响的行数。
  // rowCountResult 方法的作用就是将这个原始的 int 数值，根据你在 Mapper 接口中定义的返回类型，进行“包装”或“转换”。
  private Object rowCountResult(int rowCount) {
    final Object result;
    // 如果你在接口里写的是 void deleteUser(id)，那么 MyBatis 会执行 SQL，但直接忽略受影响行数，返回 null。
    if (method.returnsVoid()) {
      result = null;
      // 场景 B：返回 Integer / int
    } else if (Integer.class.equals(method.getReturnType()) || Integer.TYPE.equals(method.getReturnType())) {
      result = rowCount;
    } else if (Long.class.equals(method.getReturnType()) || Long.TYPE.equals(method.getReturnType())) {
      result = (long) rowCount;
    } else if (Boolean.class.equals(method.getReturnType()) || Boolean.TYPE.equals(method.getReturnType())) {
      result = rowCount > 0;
    } else {
      throw new BindingException(
          "Mapper method '" + command.getName() + "' has an unsupported return type: " + method.getReturnType());
    }
    return result;
  }

  private void executeWithResultHandler(SqlSession sqlSession, Object[] args) {
    MappedStatement ms = sqlSession.getConfiguration().getMappedStatement(command.getName());
    if (!StatementType.CALLABLE.equals(ms.getStatementType())
        && void.class.equals(ms.getResultMaps().get(0).getType())) {
      throw new BindingException(
          "method " + command.getName() + " needs either a @ResultMap annotation, a @ResultType annotation,"
              + " or a resultType attribute in XML so a ResultHandler can be used as a parameter.");
    }
    Object param = method.convertArgsToSqlCommandParam(args);
    if (method.hasRowBounds()) {
      RowBounds rowBounds = method.extractRowBounds(args);
      sqlSession.select(command.getName(), param, rowBounds, method.extractResultHandler(args));
    } else {
      sqlSession.select(command.getName(), param, method.extractResultHandler(args));
    }
  }

  // 负责处理所有返回多条记录的场景（如 List、Set、数组等）。
  // 设计精华在于：它不仅从数据库查出数据，还解决了 “底层返回的总是 ArrayList” 与 “接口定义可能是各种集合或数组” 之间的类型不匹配问题。
  private <E> Object executeForMany(SqlSession sqlSession, Object[] args) {
    List<E> result;
    Object param = method.convertArgsToSqlCommandParam(args);
    if (method.hasRowBounds()) {
      RowBounds rowBounds = method.extractRowBounds(args);
      result = sqlSession.selectList(command.getName(), param, rowBounds);
    } else {
      result = sqlSession.selectList(command.getName(), param);
    }
    // issue #510 Collections & arrays support
    if (!method.getReturnType().isAssignableFrom(result.getClass())) {
      if (method.getReturnType().isArray()) {
        return convertToArray(result);
      }
      return convertToDeclaredCollection(sqlSession.getConfiguration(), result);
    }
    return result;
  }

  private <T> Cursor<T> executeForCursor(SqlSession sqlSession, Object[] args) {
    Cursor<T> result;
    Object param = method.convertArgsToSqlCommandParam(args);
    if (method.hasRowBounds()) {
      RowBounds rowBounds = method.extractRowBounds(args);
      result = sqlSession.selectCursor(command.getName(), param, rowBounds);
    } else {
      result = sqlSession.selectCursor(command.getName(), param);
    }
    return result;
  }

  private <E> Object convertToDeclaredCollection(Configuration config, List<E> list) {
    Object collection = config.getObjectFactory().create(method.getReturnType());
    MetaObject metaObject = config.newMetaObject(collection);
    metaObject.addAll(list);
    return collection;
  }

  @SuppressWarnings("unchecked")
  private <E> Object convertToArray(List<E> list) {
    Class<?> arrayComponentType = method.getReturnType().getComponentType();
    Object array = Array.newInstance(arrayComponentType, list.size());
    if (!arrayComponentType.isPrimitive()) {
      return list.toArray((E[]) array);
    }
    for (int i = 0; i < list.size(); i++) {
      Array.set(array, i, list.get(i));
    }
    return array;
  }

  private <K, V> Map<K, V> executeForMap(SqlSession sqlSession, Object[] args) {
    Map<K, V> result;
    Object param = method.convertArgsToSqlCommandParam(args);
    if (method.hasRowBounds()) {
      RowBounds rowBounds = method.extractRowBounds(args);
      result = sqlSession.selectMap(command.getName(), param, method.getMapKey(), rowBounds);
    } else {
      result = sqlSession.selectMap(command.getName(), param, method.getMapKey());
    }
    return result;
  }

  public static class ParamMap<V> extends HashMap<String, V> {

    private static final long serialVersionUID = -2212268410512043556L;

    @Override
    public V get(Object key) {
      if (!super.containsKey(key)) {
        throw new BindingException("Parameter '" + key + "' not found. Available parameters are " + keySet());
      }
      return super.get(key);
    }

  }

  // 核心职责是：确定要执行哪条 SQL 以及这条 SQL 的动作类型。
  public static class SqlCommand {
    // 存储 SQL 语句的唯一标识符（Statement ID）
    // 通常是 接口全限定名.方法名（例如 com.dao.UserMapper.selectById）。MyBatis 随后会拿着这个 ID 去 Configuration 中提取真正的 SQL 文本
    private final String name;
    // 存储 SQL 的操作类型。
    // 枚举值，包括 INSERT, UPDATE, DELETE, SELECT, FLUSH 或 UNKNOWN。MapperMethod 靠这个类型来决定调用 SqlSession 的哪个方法。
    private final SqlCommandType type;

    public SqlCommand(Configuration configuration, Class<?> mapperInterface, Method method) {
      // 获取当前调用的方法名和声明该方法的类
      final String methodName = method.getName();
      final Class<?> declaringClass = method.getDeclaringClass();
      // 尝试定位 SQL
      MappedStatement ms = resolveMappedStatement(mapperInterface, methodName, declaringClass, configuration);
      if (ms == null) {
        if (method.getAnnotation(Flush.class) == null) {
          throw new BindingException(
              "Invalid bound statement (not found): " + mapperInterface.getName() + "." + methodName);
        }
        name = null;
        type = SqlCommandType.FLUSH;
      } else {
        name = ms.getId();
        type = ms.getSqlCommandType();
        if (type == SqlCommandType.UNKNOWN) {
          throw new BindingException("Unknown execution method for: " + name);
        }
      }
    }

    public String getName() {
      return name;
    }

    public SqlCommandType getType() {
      return type;
    }

    // MyBatis 支撑 Mapper 接口继承 机制的核心算法。它的任务是在复杂的接口继承树中，准确地找到与当前 Java 方法对应的 SQL 映射定义
    private MappedStatement resolveMappedStatement(Class<?> mapperInterface, String methodName, Class<?> declaringClass,
        Configuration configuration) {
      // 首先尝试用“当前接口的全限定名 + 方法名”作为 ID 去 Configuration 缓存中查找。
      String statementId = mapperInterface.getName() + "." + methodName;
      if (configuration.hasStatement(statementId)) {
        return configuration.getMappedStatement(statementId);
      }
      // 如果当前检查的接口正好就是声明该方法的那个原始接口（declaringClass），且第一步没找到，说明真的没定义。
      if (mapperInterface.equals(declaringClass)) {
        return null;
      }
      // 向上递归搜索（继承链支持）
      for (Class<?> superInterface : mapperInterface.getInterfaces()) {
        if (declaringClass.isAssignableFrom(superInterface)) {
          MappedStatement ms = resolveMappedStatement(superInterface, methodName, declaringClass, configuration);
          if (ms != null) {
            return ms;
          }
        }
      }
      return null;
    }
  }

  // 解析并存储 Java 接口方法的各种元数据
  // 如果说 SqlCommand 关心的是“要去哪（SQL）”，那么 MethodSignature 关心的就是“带什么去（参数）”以及“拿什么回来（返回值）”。
  // MethodSignature 在 MapperMethod 初始化时，通过反射对接口方法进行“全方位体检”。它会预先判断：
  // 这个方法是返回单条数据、多条数据（Collection/Array）、Map 还是 Cursor？
  // 参数里是否有特殊对象，比如分页用的 RowBounds 或回调用的 ResultHandler？
  // 如果返回 Map，是用哪个字段作为 Key（通过 @MapKey）？
  public static class MethodSignature {
    // 是否返回多个结果（数组或集合类型）。
    private final boolean returnsMany;
    // 是否返回 Map 类型的结果。
    private final boolean returnsMap;
    // 方法返回值是否为 void。
    private final boolean returnsVoid;
    // 是否返回 org.apache.ibatis.cursor.Cursor（流式结果集）。
    private final boolean returnsCursor;
    // 是否返回 java.util.Optional 类型（Java 8+ 支持）。
    private final boolean returnsOptional;
    // 方法真实的返回类型（处理了泛型解析后的类型）。
    private final Class<?> returnType;
    // 如果返回 Map，存储 @MapKey 注解指定的字段名。
    private final String mapKey;
    // ResultHandler 参数在方法参数列表中的索引位置（如果没有则为 null）。
    private final Integer resultHandlerIndex;
    // RowBounds 参数在方法参数列表中的索引位置（如果没有则为 null）。
    private final Integer rowBoundsIndex;
    // 参数名称解析器。负责将方法参数名与实际传入的值进行映射绑定。
    private final ParamNameResolver paramNameResolver;

    public MethodSignature(Configuration configuration, Class<?> mapperInterface, Method method) {
      // 智能解析返回类型，能够正确处理复杂的继承和泛型。
      Type resolvedReturnType = TypeParameterResolver.resolveReturnType(method, mapperInterface);
      if (resolvedReturnType instanceof Class<?>) {
        this.returnType = (Class<?>) resolvedReturnType;
      } else if (resolvedReturnType instanceof ParameterizedType) {
        this.returnType = (Class<?>) ((ParameterizedType) resolvedReturnType).getRawType();
      } else {
        this.returnType = method.getReturnType();
      }
      this.returnsVoid = void.class.equals(this.returnType);
      this.returnsMany = configuration.getObjectFactory().isCollection(this.returnType) || this.returnType.isArray();
      this.returnsCursor = Cursor.class.equals(this.returnType);
      this.returnsOptional = Optional.class.equals(this.returnType);
      this.mapKey = getMapKey(method);
      this.returnsMap = this.mapKey != null;
      this.rowBoundsIndex = getUniqueParamIndex(method, RowBounds.class);
      this.resultHandlerIndex = getUniqueParamIndex(method, ResultHandler.class);
      this.paramNameResolver = new ParamNameResolver(configuration, method, mapperInterface);
    }

    public Object convertArgsToSqlCommandParam(Object[] args) {
      return paramNameResolver.getNamedParams(args);
    }

    public boolean hasRowBounds() {
      return rowBoundsIndex != null;
    }

    public RowBounds extractRowBounds(Object[] args) {
      return hasRowBounds() ? (RowBounds) args[rowBoundsIndex] : null;
    }

    public boolean hasResultHandler() {
      return resultHandlerIndex != null;
    }

    public ResultHandler extractResultHandler(Object[] args) {
      return hasResultHandler() ? (ResultHandler) args[resultHandlerIndex] : null;
    }

    public Class<?> getReturnType() {
      return returnType;
    }

    public boolean returnsMany() {
      return returnsMany;
    }

    public boolean returnsMap() {
      return returnsMap;
    }

    public boolean returnsVoid() {
      return returnsVoid;
    }

    public boolean returnsCursor() {
      return returnsCursor;
    }

    /**
     * return whether return type is {@code java.util.Optional}.
     *
     * @return return {@code true}, if return type is {@code java.util.Optional}
     *
     * @since 3.5.0
     */
    public boolean returnsOptional() {
      return returnsOptional;
    }

    private Integer getUniqueParamIndex(Method method, Class<?> paramType) {
      Integer index = null;
      final Class<?>[] argTypes = method.getParameterTypes();
      for (int i = 0; i < argTypes.length; i++) {
        if (paramType.isAssignableFrom(argTypes[i])) {
          if (index != null) {
            throw new BindingException(
                method.getName() + " cannot have multiple " + paramType.getSimpleName() + " parameters");
          }
          index = i;
        }
      }
      return index;
    }

    public String getMapKey() {
      return mapKey;
    }

    private String getMapKey(Method method) {
      String mapKey = null;
      if (Map.class.isAssignableFrom(method.getReturnType())) {
        final MapKey mapKeyAnnotation = method.getAnnotation(MapKey.class);
        if (mapKeyAnnotation != null) {
          mapKey = mapKeyAnnotation.value();
        }
      }
      return mapKey;
    }
  }

}
