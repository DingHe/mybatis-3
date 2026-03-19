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
package org.apache.ibatis.cursor;

/**
 * Cursor contract to handle fetching items lazily using an Iterator. Cursors are a perfect fit to handle millions of
 * items queries that would not normally fit in memory. If you use collections in resultMaps then cursor SQL queries
 * must be ordered (resultOrdered="true") using the id columns of the resultMap.
 *
 * @author Guillaume Darmont / guillaume@dropinocean.com
 */
// 专门用于处理超大数据量的查询场景
// 在传统的 MyBatis 查询中，无论是返回 List 还是 Map，MyBatis 都会一次性将所有结果集加载到内存中。如果数据库有一百万条数据，这会导致内存溢出（OOM）。
// Cursor（游标）的作用类似于数据库层面的游标：
// 延迟加载（Lazy Fetching）：它不会一次性把所有数据查出来，而是当你通过迭代器（Iterator）遍历时，才逐条或逐批从数据库抓取。
// 内存友好：它是处理千万级数据查询的最佳方案，因为它在内存中只保留当前处理的一小部分数据。
// 资源管理：它继承了 AutoCloseable，意味着它持有数据库连接资源，使用完必须手动关闭或使用 try-with-resources。
public interface Cursor<T> extends AutoCloseable, Iterable<T> {

  /**
   * @return true if the cursor has started to fetch items from database.
   */
  // 判断游标是否处于开启状态。
  boolean isOpen();

  /**
   * @return true if the cursor is fully consumed and has returned all elements matching the query.
   */
  // 判断游标是否已被完全消耗。
  boolean isConsumed();

  /**
   * Get the current item index. The first item has the index 0.
   *
   * @return -1 if the first cursor item has not been retrieved. The index of the current item retrieved.
   */
  // 获取当前已检索条目的索引（下标）
  int getCurrentIndex();

  /**
   * Closes the cursor.
   */
  // 关闭游标并释放相关数据库资源。
  @Override
  void close();
}
