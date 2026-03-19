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
package org.apache.ibatis.session;

/**
 * @author Clinton Begin
 */
// 允许开发者绕过 MyBatis 默认的“一次性将所有结果装入 List”的行为，实现对查询结果的流式处理或自定义聚合。
// ResultHandler 的核心作用是逐行处理数据库返回的结果。
// 通常情况下，MyBatis 执行 selectList 会在内部创建一个 DefaultResultHandler（它内部持有一个 ArrayList），每读到一行数据就 add 进去，最后返回整个 List。而当你通过
// SqlSession 传递一个自定义的 ResultHandler 时，MyBatis 每解析完一个对象，就会调用一次你的 handleResult 方法。
// 大数据量导出：避免将百万级数据同时加载到内存引发 OOM，可以边读边写入 Excel 或 CSV。
// 自定义 Map 聚合：根据业务逻辑将结果聚合为特定的数据结构（如 Map<GroupId, List<User>>）。
public interface ResultHandler<T> {

  void handleResult(ResultContext<? extends T> resultContext);

}
