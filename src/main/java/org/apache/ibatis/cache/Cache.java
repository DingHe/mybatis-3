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
package org.apache.ibatis.cache;

import java.util.concurrent.locks.ReadWriteLock;

/**
 * SPI for cache providers.
 * <p>
 * One instance of cache will be created for each namespace.
 * <p>
 * The cache implementation must have a constructor that receives the cache id as an String parameter.
 * <p>
 * MyBatis will pass the namespace as id to the constructor.
 *
 * <pre>
 * public MyCache(final String id) {
 *   if (id == null) {
 *     throw new IllegalArgumentException("Cache instances require an ID");
 *   }
 *   this.id = id;
 *   initialize();
 * }
 * </pre>
 *
 * @author Clinton Begin
 */
// MyBatis 缓存系统的核心， 是所有缓存实现的顶级接口。
// Cache 接口是一个 SPI（Service Provider Interface），即为缓存提供商设计的插件式接口。它的主要作用包括：
// 定义标准：规定了 MyBatis 缓存组件必须具备的基础功能（存、取、删、清空）。
// 支持扩展：MyBatis 默认提供了一些实现（如 PerpetualCache），但也允许开发者集成第三方缓存（如 Redis、Ehcache、Hazelcast）。
// 命名空间隔离：在 MyBatis 中，每一个 Mapper 的 namespace 都会对应一个唯一的 Cache 实例。
// 装饰器模式基石：MyBatis 利用这个接口通过装饰器模式实现了多种高级功能，例如：
// LruCache（最近最少使用回收）
// FifoCache（先进先出回收）
// LoggingCache（打印缓存命中率日志）
// SynchronizedCache（线程安全同步）
public interface Cache {

  /**
   * @return The identifier of this cache
   */
  // 获取该缓存实例的唯一标识。
  String getId();

  /**
   * @param key
   *          Can be any object but usually it is a {@link CacheKey}
   * @param value
   *          The result of a select.
   */
  // 向缓存中存入一个对象。
  // key：通常是一个 CacheKey 对象，它封装了 SQL 语句、参数、分页等信息，确保查询的唯一性。
  // 在事务提交前，数据通常暂存在 TransactionalCache 中，提交时才真正调用此方法写入底层缓存。
  void putObject(Object key, Object value);

  /**
   * @param key
   *          The key
   *
   * @return The object stored in the cache.
   */
  // 根据指定的 Key 从缓存中获取对象。
  Object getObject(Object key);

  /**
   * As of 3.3.0 this method is only called during a rollback for any previous value that was missing in the cache. This
   * lets any blocking cache to release the lock that may have previously put on the key. A blocking cache puts a lock
   * when a value is null and releases it when the value is back again. This way other threads will wait for the value
   * to be available instead of hitting the database.
   *
   * @param key
   *          The key
   *
   * @return Not used
   */
  // 从缓存中移除指定的对象。
  // 重要变化：自 3.3.0 版本起，该方法主要用于事务回滚阶段。
  Object removeObject(Object key);

  /**
   * Clears this cache instance.
   */
  // 清空当前缓存实例中的所有数据。
  void clear();

  /**
   * Optional. This method is not called by the core.
   *
   * @return The number of elements stored in the cache (not its capacity).
   */
  // 获取当前缓存中存储的条目数量。
  int getSize();

  /**
   * Optional. As of 3.2.6 this method is no longer called by the core.
   * <p>
   * Any locking needed by the cache must be provided internally by the cache provider.
   *
   * @return A ReadWriteLock
   */
  // 返回用于该缓存的读写锁。
  default ReadWriteLock getReadWriteLock() {
    return null;
  }

}
