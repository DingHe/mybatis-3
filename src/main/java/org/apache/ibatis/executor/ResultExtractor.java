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
package org.apache.ibatis.executor;

import java.lang.reflect.Array;
import java.util.List;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.session.Configuration;

/**
 * @author Andrew Gustafson
 */
// 主要用于结果类型的后期转换与提取
// 在 MyBatis 执行查询时，底层的 ResultSetHandler 通常会先将数据库结果集解析成一个 java.util.List。然而，开发者在 Mapper 接口中定义的返回类型可能是多样化的（比如一个普通的 User
// 对象、一个 User[] 数组、或者一个自定义的 Set 集合）。
// ResultExtractor 的核心作用就是：将底层的 List 结果按照 Mapper 接口定义的 targetType（目标类型）进行“重塑”和“提取”。它决定了最终交给用户的是一个单对象、一个数组还是一个特定的集合容器。
public class ResultExtractor {
  // 持有 MyBatis 的全局配置对象
  private final Configuration configuration;
  // 对象工厂。
  // 用于根据目标类型（如 HashSet、ArrayList 等）创建新的实例。它比直接 new 对象更具扩展性，允许用户自定义对象的实例化逻辑。
  private final ObjectFactory objectFactory;

  public ResultExtractor(Configuration configuration, ObjectFactory objectFactory) {
    this.configuration = configuration;
    this.objectFactory = objectFactory;
  }

  // 根据你定义的 Mapper 接口返回类型，决定如何把数据库查出来的 List 包装成最终的对象。
  public Object extractObjectFromList(List<Object> list, Class<?> targetType) {
    Object value = null;
    // 如果你的 Mapper 接口返回类型本身就是 List 或者 Collection（且当前 list 实例可以赋值给它）
    if (targetType != null && targetType.isAssignableFrom(list.getClass())) {
      value = list;
      // 如果目标类型是集合（如 Set、SortedSet 等），但不是上面提到的 List。
    } else if (targetType != null && objectFactory.isCollection(targetType)) {
      // 利用 objectFactory 创建一个该类型的实例（比如 HashSet）。
      // 利用 MyBatis 的 MetaObject 包装这个新集合
      value = objectFactory.create(targetType);
      MetaObject metaObject = configuration.newMetaObject(value);
      metaObject.addAll(list);
      // 目标是 Array
    } else if (targetType != null && targetType.isArray()) {
      Class<?> arrayComponentType = targetType.getComponentType();
      Object array = Array.newInstance(arrayComponentType, list.size());
      if (arrayComponentType.isPrimitive()) {
        for (int i = 0; i < list.size(); i++) {
          Array.set(array, i, list.get(i));
        }
        value = array;
      } else {
        value = list.toArray((Object[]) array);
      }
      // 异常防御：期待单行却返回多行
    } else if (list != null && list.size() > 1) {
      throw new ExecutorException("Statement returned more than one row, where no more than one was expected.");
    } else if (list != null && list.size() == 1) {
      value = list.get(0);
    }
    return value;
  }
}
