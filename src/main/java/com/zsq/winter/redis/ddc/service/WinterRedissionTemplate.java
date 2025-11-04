package com.zsq.winter.redis.ddc.service;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.*;
import org.redisson.api.listener.MessageListener;
import org.redisson.api.listener.PatternMessageListener;
import org.redisson.api.listener.PatternStatusListener;
import org.redisson.client.codec.Codec;
import org.springframework.util.ObjectUtils;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
public class WinterRedissionTemplate {

    private final RedissonClient redissonClient;
    // 布隆过滤器缓存，用于快速访问已创建的过滤器
    private final Map<String, RBloomFilter<Object>> bloomFilterCache = new ConcurrentHashMap<>();

    public WinterRedissionTemplate(RedissonClient winterRedissonClient) {
        this.redissonClient = winterRedissonClient;
    }

    /**
     * 获取脚本执行器（使用默认编解码器）
     */
    public RScript getScript() {
        return redissonClient.getScript();
    }

    /**
     * 获取脚本执行器（指定编解码器）
     */
    public RScript getScript(Codec codec) {
        return redissonClient.getScript(codec);
    }

    // ================ 分布式锁相关方法 ================

    /**
     * 获取分布式锁
     * 适用场景：常规的分布式锁操作
     * @param lockKey 锁的key
     * @return RLock实例
     */
    public RLock getLock(String lockKey) {
        return redissonClient.getLock(lockKey);
    }

    /**
     * 获取公平锁
     * 适用场景：需要保证获取锁的顺序性
     * @param lockKey 锁的key
     * @return RLock实例
     */
    public RLock getFairLock(String lockKey) {
        return redissonClient.getFairLock(lockKey);
    }

    /**
     * 获取读写锁
     * 适用场景：分布式读写分离操作
     * @param lockKey 锁的key
     * @return RReadWriteLock实例
     */
    public RReadWriteLock getReadWriteLock(String lockKey) {
        return redissonClient.getReadWriteLock(lockKey);
    }

    /**
     * 获取联锁（MultiLock）
     * 适用场景：多服务加锁，要求所有锁都获取成功
     * @param lockKeys 多个锁的key
     * @return RLock实例
     */
    public RLock getMultiLock(String... lockKeys) {
        RLock[] locks = new RLock[lockKeys.length];
        for (int i = 0; i < lockKeys.length; i++) {
            locks[i] = getLock(lockKeys[i]);
        }
        return redissonClient.getMultiLock(locks);
    }

    /**
     * 获取红锁（RedLock）
     * 适用场景：Redis集群锁定多个节点
     * @param lockKey 锁的key
     * @return RLock实例
     */
    public RLock getRedLock(String lockKey) {
        return redissonClient.getRedLock(getLock(lockKey));
    }

    /**
     * 尝试获取锁
     * 适用场景：需要等待的锁操作
     * @param lockKey 锁的key
     * @param waitTime 等待时间
     * @param leaseTime 持有锁的时间
     * @param unit 时间单位
     * @return 是否获取成功
     */
    public boolean tryLock(String lockKey, long waitTime, long leaseTime, TimeUnit unit) {
        try {
            RLock lock = getLock(lockKey);
            return lock.tryLock(waitTime, leaseTime, unit);
        } catch (InterruptedException e) {
            log.error("获取锁失败，key: {}", lockKey, e);
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 使用锁执行代码
     * 适用场景：需要加锁执行的代码块
     * @param lockKey 锁的key
     * @param waitTime 等待时间
     * @param leaseTime 持有锁的时间
     * @param unit 时间单位
     * @param runnable 要执行的代码
     * @return 是否执行成功
     */
    public boolean executeWithLock(String lockKey, long waitTime, long leaseTime, TimeUnit unit, Runnable runnable) {
        RLock lock = getLock(lockKey);
        try {
            if (lock.tryLock(waitTime, leaseTime, unit)) {
                try {
                    runnable.run();
                    return true;
                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            }
        } catch (InterruptedException e) {
            log.error("执行加锁代码失败，key: {}", lockKey, e);
            Thread.currentThread().interrupt();
        }
        return false;
    }

    /**
     * 使用锁执行有返回值的代码
     * 适用场景：需要加锁执行并返回结果的代码块
     * @param lockKey 锁的key
     * @param waitTime 等待时间
     * @param leaseTime 持有锁的时间
     * @param unit 时间单位
     * @param supplier 要执行的代码
     * @return 执行结果，如果获取锁失败返回null
     */
    public <T> T executeWithLockReturn(String lockKey, long waitTime, long leaseTime, TimeUnit unit, Supplier<T> supplier) {
        RLock lock = getLock(lockKey);
        try {
            if (lock.tryLock(waitTime, leaseTime, unit)) {
                try {
                    return supplier.get();
                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            }
        } catch (InterruptedException e) {
            log.error("执行加锁代码失败，key: {}", lockKey, e);
            Thread.currentThread().interrupt();
        }
        return null;
    }

    /**
     * 获取读写锁并执行读操作
     * 适用场景：读多写少的分布式场景
     * @param lockKey 锁的key
     * @param supplier 要执行的读操作
     * @return 读操作的结果
     */
    public <T> T executeWithReadLock(String lockKey, Supplier<T> supplier) {
        RReadWriteLock readWriteLock = getReadWriteLock(lockKey);
        RLock readLock = readWriteLock.readLock();
        readLock.lock();
        try {
            return supplier.get();
        } finally {
            readLock.unlock();
        }
    }

    /**
     * 获取读写锁并执行写操作
     * 适用场景：需要独占写的分布式场景
     * @param lockKey 锁的key
     * @param runnable 要执行的写操作
     */
    public void executeWithWriteLock(String lockKey, Runnable runnable) {
        RReadWriteLock readWriteLock = getReadWriteLock(lockKey);
        RLock writeLock = readWriteLock.writeLock();
        writeLock.lock();
        try {
            runnable.run();
        } finally {
            writeLock.unlock();
        }
    }

    // ================ 信号量和计数器相关方法 ================

    /**
     * 获取信号量
     * 适用场景：限流、并发访问控制
     * @param key 信号量的key
     * @return RSemaphore实例
     */
    public RSemaphore getSemaphore(String key) {
        return redissonClient.getSemaphore(key);
    }

    /**
     * 获取计数器
     * 适用场景：微服务任务协调
     * @param key 计数器的key
     * @return RCountDownLatch实例
     */
    public RCountDownLatch getCountDownLatch(String key) {
        return redissonClient.getCountDownLatch(key);
    }

    /**
     * 使用信号量执行代码
     * 适用场景：限流场景下的代码执行
     * @param key 信号量的key
     * @param permits 需要的许可数
     * @param timeout 超时时间
     * @param unit 时间单位
     * @param runnable 要执行的代码
     * @return 是否执行成功
     */
    public boolean executeWithSemaphore(String key, int permits, long timeout, TimeUnit unit, Runnable runnable) {
        RSemaphore semaphore = getSemaphore(key);
        try {
            if (semaphore.tryAcquire(permits, timeout, unit)) {
                try {
                    runnable.run();
                    return true;
                } finally {
                    semaphore.release(permits);
                }
            }
        } catch (InterruptedException e) {
            log.error("信号量获取失败，key: {}", key, e);
            Thread.currentThread().interrupt();
        }
        return false;
    }

    // ================ String（字符串）操作 ================
    
    /**
     * 获取字符串对象
     * <p>
     * RBucket是Redisson对Redis String类型的封装，支持各种数据类型的存储。
     * 适用场景：存储单个对象、缓存数据、计数器等
     * </p>
     *
     * @param key 键
     * @param <T> 值的类型
     * @return RBucket实例
     */
    public <T> RBucket<T> getBucket(String key) {
        return redissonClient.getBucket(key);
    }
    
    /**
     * 设置字符串值
     * <p>
     * 将指定的值存储到Redis中，如果键已存在则覆盖原有值
     * </p>
     *
     * @param key   键
     * @param value 值
     * @param <T>   值的类型
     */
    public <T> void set(String key, T value) {
        RBucket<T> bucket = redissonClient.getBucket(key);
        bucket.set(value);
    }
    
    /**
     * 设置字符串值并指定过期时间
     * <p>
     * 将指定的值存储到Redis中，并设置过期时间，过期后自动删除
     * </p>
     *
     * @param key      键
     * @param value    值
     * @param timeout  过期时间
     * @param timeUnit 时间单位
     * @param <T>      值的类型
     */
    public <T> void set(String key, T value, long timeout, TimeUnit timeUnit) {
        // 将时间单位转换为Duration
        Duration duration = Duration.ofMillis(timeUnit.toMillis(timeout));
        redissonClient.getBucket(key).set(value, duration);
    }
    
    /**
     * 获取字符串值
     * <p>
     * 从Redis中获取指定键的值
     * </p>
     *
     * @param key 键
     * @param <T> 值的类型
     * @return 值，如果不存在返回null
     */
    public <T> T get(String key) {
        RBucket<T> bucket = redissonClient.getBucket(key);
        return bucket.get();
    }
    
    /**
     * 获取并删除字符串值
     * <p>
     * 原子操作：获取值的同时删除该键
     * </p>
     *
     * @param key 键
     * @param <T> 值的类型
     * @return 值，如果不存在返回null
     */
    public <T> T getAndDelete(String key) {
        RBucket<T> bucket = redissonClient.getBucket(key);
        return bucket.getAndDelete();
    }
    
    /**
     * 获取并设置新值
     * <p>
     * 原子操作：获取旧值的同时设置新值
     * </p>
     *
     * @param key      键
     * @param newValue 新值
     * @param <T>      值的类型
     * @return 旧值，如果不存在返回null
     */
    public <T> T getAndSet(String key, T newValue) {
        RBucket<T> bucket = redissonClient.getBucket(key);
        return bucket.getAndSet(newValue);
    }
    
    /**
     * 删除键
     * <p>
     * 从Redis中删除指定的键
     * </p>
     *
     * @param key 键
     * @return 是否删除成功
     */
    public boolean delete(String key) {
        RBucket<Object> bucket = redissonClient.getBucket(key);
        return bucket.delete();
    }
    
    /**
     * 判断键是否存在
     * <p>
     * 检查指定的键是否存在于Redis中
     * </p>
     *
     * @param key 键
     * @return 存在返回true，否则返回false
     */
    public boolean isExists(String key) {
        RBucket<Object> bucket = redissonClient.getBucket(key);
        return bucket.isExists();
    }

    // ================ Hash（哈希表）操作 ================
    
    /**
     * 获取哈希表对象
     * <p>
     * RMap是Redisson对Redis Hash类型的封装，类似于Java的Map接口。
     * 适用场景：存储对象的多个字段、用户信息、配置信息等
     * </p>
     *
     * @param key 键
     * @param <K> 字段类型
     * @param <V> 值类型
     * @return RMap实例
     */
    public <K, V> RMap<K, V> getMap(String key) {
        return redissonClient.getMap(key);
    }
    
    /**
     * 设置哈希表字段值
     * <p>
     * 在指定的哈希表中设置字段的值
     * </p>
     *
     * @param key   哈希表键
     * @param field 字段
     * @param value 值
     * @param <K>   字段类型
     * @param <V>   值类型
     * @return 之前的值，如果不存在返回null
     */
    public <K, V> V hPut(String key, K field, V value) {
        RMap<K, V> map = redissonClient.getMap(key);
        return map.put(field, value);
    }
    
    /**
     * 获取哈希表字段值
     * <p>
     * 从指定的哈希表中获取字段的值
     * </p>
     *
     * @param key   哈希表键
     * @param field 字段
     * @param <K>   字段类型
     * @param <V>   值类型
     * @return 字段值，如果不存在返回null
     */
    public <K, V> V hGet(String key, K field) {
        RMap<K, V> map = redissonClient.getMap(key);
        return map.get(field);
    }
    
    /**
     * 删除哈希表字段
     * <p>
     * 从指定的哈希表中删除一个或多个字段
     * </p>
     *
     * @param key    哈希表键
     * @param fields 要删除的字段
     * @param <K>    字段类型
     * @return 成功删除的字段数量
     */
    @SafeVarargs
    public final <K> long hDel(String key, K... fields) {
        RMap<K, Object> map = redissonClient.getMap(key);
        return map.fastRemove(fields);
    }
    
    /**
     * 判断哈希表字段是否存在
     * <p>
     * 检查指定的哈希表中是否存在某个字段
     * </p>
     *
     * @param key   哈希表键
     * @param field 字段
     * @param <K>   字段类型
     * @return 存在返回true，否则返回false
     */
    public <K> boolean hExists(String key, K field) {
        RMap<K, Object> map = redissonClient.getMap(key);
        return map.containsKey(field);
    }
    
    /**
     * 获取哈希表所有字段和值
     * <p>
     * 获取指定哈希表的所有字段和对应的值
     * </p>
     *
     * @param key 哈希表键
     * @param <K> 字段类型
     * @param <V> 值类型
     * @return 包含所有字段和值的Map
     */
    public <K, V> Map<K, V> hGetAll(String key) {
        RMap<K, V> map = redissonClient.getMap(key);
        return map.readAllMap();
    }
    
    /**
     * 获取哈希表所有字段
     * <p>
     * 获取指定哈希表的所有字段名
     * </p>
     *
     * @param key 哈希表键
     * @param <K> 字段类型
     * @return 包含所有字段的Set集合
     */
    public <K> Set<K> hKeys(String key) {
        RMap<K, Object> map = redissonClient.getMap(key);
        return map.readAllKeySet();
    }
    
    /**
     * 获取哈希表所有值
     * <p>
     * 获取指定哈希表的所有值
     * </p>
     *
     * @param key 哈希表键
     * @param <V> 值类型
     * @return 包含所有值的Collection集合
     */
    public <V> Collection<V> hValues(String key) {
        RMap<Object, V> map = redissonClient.getMap(key);
        return map.readAllValues();
    }
    
    /**
     * 获取哈希表字段数量
     * <p>
     * 获取指定哈希表中字段的数量
     * </p>
     *
     * @param key 哈希表键
     * @return 字段数量
     */
    public int hSize(String key) {
        RMap<Object, Object> map = redissonClient.getMap(key);
        return map.size();
    }

    // ================ List（列表）操作 ================
    
    /**
     * 获取列表对象
     * <p>
     * RList是Redisson对Redis List类型的封装，类似于Java的List接口。
     * 适用场景：消息队列、时间线、最新列表等
     * </p>
     *
     * @param key 键
     * @param <V> 元素类型
     * @return RList实例
     */
    public <V> RList<V> getList(String key) {
        return redissonClient.getList(key);
    }
    
    /**
     * 从列表左侧添加元素
     * <p>
     * 将一个或多个值插入到列表头部（左侧）
     * </p>
     *
     * @param key    列表键
     * @param values 要添加的值
     * @param <V>    元素类型
     * @return 添加后列表的长度
     */
    @SafeVarargs
    public final <V> boolean lPush(String key, V... values) {
        RList<V> list = redissonClient.getList(key);
        return list.addAll(0, Arrays.asList(values));
    }
    
    /**
     * 从列表右侧添加元素
     * <p>
     * 将一个或多个值插入到列表尾部（右侧）
     * </p>
     *
     * @param key    列表键
     * @param values 要添加的值
     * @param <V>    元素类型
     * @return 是否添加成功
     */
    @SafeVarargs
    public final <V> boolean rPush(String key, V... values) {
        RList<V> list = redissonClient.getList(key);
        return list.addAll(Arrays.asList(values));
    }
    
    /**
     * 从列表左侧弹出元素
     * <p>
     * 移除并返回列表的第一个元素（左侧）
     * </p>
     *
     * @param key 列表键
     * @param <V> 元素类型
     * @return 弹出的元素，如果列表为空返回null
     */
    public <V> V lPop(String key) {
        RList<V> list = redissonClient.getList(key);
        return list.isEmpty() ? null : list.remove(0);
    }
    
    /**
     * 从列表右侧弹出元素
     * <p>
     * 移除并返回列表的最后一个元素（右侧）
     * </p>
     *
     * @param key 列表键
     * @param <V> 元素类型
     * @return 弹出的元素，如果列表为空返回null
     */
    public <V> V rPop(String key) {
        RList<V> list = redissonClient.getList(key);
        return list.isEmpty() ? null : list.remove(list.size() - 1);
    }
    
    /**
     * 获取列表指定范围的元素
     * <p>
     * 返回列表中指定区间内的元素，区间以偏移量start和end指定
     * </p>
     *
     * @param key   列表键
     * @param start 开始位置（0表示第一个元素）
     * @param end   结束位置（-1表示最后一个元素）
     * @param <V>   元素类型
     * @return 指定范围内的元素列表
     */
    public <V> List<V> lRange(String key, int start, int end) {
        RList<V> list = redissonClient.getList(key);
        return list.range(start, end);
    }
    
    /**
     * 获取列表长度
     * <p>
     * 返回列表的长度
     * </p>
     *
     * @param key 列表键
     * @return 列表长度
     */
    public int lSize(String key) {
        RList<Object> list = redissonClient.getList(key);
        return list.size();
    }
    
    /**
     * 获取列表指定索引的元素
     * <p>
     * 通过索引获取列表中的元素
     * </p>
     *
     * @param key   列表键
     * @param index 索引位置
     * @param <V>   元素类型
     * @return 指定索引的元素
     */
    public <V> V lIndex(String key, int index) {
        RList<V> list = redissonClient.getList(key);
        return list.get(index);
    }

    // ================ Set（集合）操作 ================
    
    /**
     * 获取集合对象
     * <p>
     * RSet是Redisson对Redis Set类型的封装，类似于Java的Set接口。
     * 适用场景：标签、好友关系、去重等
     * </p>
     *
     * @param key 键
     * @param <V> 元素类型
     * @return RSet实例
     */
    public <V> RSet<V> getSet(String key) {
        return redissonClient.getSet(key);
    }
    
    /**
     * 向集合添加元素
     * <p>
     * 向集合中添加一个或多个成员
     * </p>
     *
     * @param key    集合键
     * @param values 要添加的值
     * @param <V>    元素类型
     * @return 是否添加成功
     */
    @SafeVarargs
    public final <V> boolean sAdd(String key, V... values) {
        RSet<V> set = redissonClient.getSet(key);
        return set.addAll(Arrays.asList(values));
    }
    
    /**
     * 移除集合中的元素
     * <p>
     * 移除集合中一个或多个成员
     * </p>
     *
     * @param key    集合键
     * @param values 要移除的值
     * @param <V>    元素类型
     * @return 是否移除成功
     */
    @SafeVarargs
    public final <V> boolean sRem(String key, V... values) {
        RSet<V> set = redissonClient.getSet(key);
        return set.removeAll(Arrays.asList(values));
    }
    
    /**
     * 判断元素是否在集合中
     * <p>
     * 判断member元素是否是集合key的成员
     * </p>
     *
     * @param key   集合键
     * @param value 要判断的值
     * @param <V>   元素类型
     * @return 存在返回true，否则返回false
     */
    public <V> boolean sIsMember(String key, V value) {
        RSet<V> set = redissonClient.getSet(key);
        return set.contains(value);
    }
    
    /**
     * 获取集合所有成员
     * <p>
     * 返回集合中的所有成员
     * </p>
     *
     * @param key 集合键
     * @param <V> 元素类型
     * @return 包含所有成员的Set集合
     */
    public <V> Set<V> sMembers(String key) {
        RSet<V> set = redissonClient.getSet(key);
        return set.readAll();
    }
    
    /**
     * 获取集合成员数量
     * <p>
     * 返回集合中元素的数量
     * </p>
     *
     * @param key 集合键
     * @return 集合成员数量
     */
    public int sSize(String key) {
        RSet<Object> set = redissonClient.getSet(key);
        return set.size();
    }
    
    /**
     * 随机获取集合中的元素
     * <p>
     * 随机返回集合中的一个元素
     * </p>
     *
     * @param key 集合键
     * @param <V> 元素类型
     * @return 随机元素
     */
    public <V> V sRandomMember(String key) {
        RSet<V> set = redissonClient.getSet(key);
        return set.random();
    }

    // ================ Sorted Set（有序集合）操作 ================
    
    /**
     * 获取有序集合对象
     * <p>
     * RScoredSortedSet是Redisson对Redis Sorted Set类型的封装。
     * 适用场景：排行榜、优先级队列、延时队列等
     * </p>
     *
     * @param key 键
     * @param <V> 元素类型
     * @return RScoredSortedSet实例
     */
    public <V> RScoredSortedSet<V> getSortedSet(String key) {
        return redissonClient.getScoredSortedSet(key);
    }
    
    /**
     * 向有序集合添加元素
     * <p>
     * 向有序集合添加一个成员，或者更新已存在成员的分数
     * </p>
     *
     * @param key   有序集合键
     * @param score 分数
     * @param value 成员值
     * @param <V>   元素类型
     * @return 是否添加成功
     */
    public <V> boolean zAdd(String key, double score, V value) {
        RScoredSortedSet<V> sortedSet = redissonClient.getScoredSortedSet(key);
        return sortedSet.add(score, value);
    }
    
    /**
     * 移除有序集合中的元素
     * <p>
     * 移除有序集合中的一个或多个成员
     * </p>
     *
     * @param key    有序集合键
     * @param values 要移除的成员
     * @param <V>    元素类型
     * @return 是否移除成功
     */
    @SafeVarargs
    public final <V> boolean zRem(String key, V... values) {
        RScoredSortedSet<V> sortedSet = redissonClient.getScoredSortedSet(key);
        return sortedSet.removeAll(Arrays.asList(values));
    }
    
    /**
     * 获取有序集合指定范围的元素（按分数从小到大）
     * <p>
     * 返回有序集合中指定区间内的成员，按分数值递增排序
     * </p>
     *
     * @param key   有序集合键
     * @param start 开始位置
     * @param end   结束位置（-1表示最后一个元素）
     * @param <V>   元素类型
     * @return 指定范围内的元素集合
     */
    public <V> Collection<V> zRange(String key, int start, int end) {
        RScoredSortedSet<V> sortedSet = redissonClient.getScoredSortedSet(key);
        return sortedSet.valueRange(start, end);
    }
    
    /**
     * 获取有序集合指定范围的元素（按分数从大到小）
     * <p>
     * 返回有序集合中指定区间内的成员，按分数值递减排序
     * </p>
     *
     * @param key   有序集合键
     * @param start 开始位置
     * @param end   结束位置（-1表示最后一个元素）
     * @param <V>   元素类型
     * @return 指定范围内的元素集合
     */
    public <V> Collection<V> zRevRange(String key, int start, int end) {
        RScoredSortedSet<V> sortedSet = redissonClient.getScoredSortedSet(key);
        return sortedSet.valueRangeReversed(start, end);
    }
    
    /**
     * 获取有序集合成员数量
     * <p>
     * 返回有序集合的成员数
     * </p>
     *
     * @param key 有序集合键
     * @return 成员数量
     */
    public int zSize(String key) {
        RScoredSortedSet<Object> sortedSet = redissonClient.getScoredSortedSet(key);
        return sortedSet.size();
    }
    
    /**
     * 获取成员的分数
     * <p>
     * 返回有序集合中，成员的分数值
     * </p>
     *
     * @param key   有序集合键
     * @param value 成员值
     * @param <V>   元素类型
     * @return 成员的分数，如果成员不存在返回null
     */
    public <V> Double zScore(String key, V value) {
        RScoredSortedSet<V> sortedSet = redissonClient.getScoredSortedSet(key);
        return sortedSet.getScore(value);
    }
    
    /**
     * 获取成员的排名（从小到大）
     * <p>
     * 返回有序集合中指定成员的排名，按分数值递增排序
     * </p>
     *
     * @param key   有序集合键
     * @param value 成员值
     * @param <V>   元素类型
     * @return 排名（0表示第一名），如果成员不存在返回null
     */
    public <V> Integer zRank(String key, V value) {
        RScoredSortedSet<V> sortedSet = redissonClient.getScoredSortedSet(key);
        return sortedSet.rank(value);
    }
    
    /**
     * 获取成员的排名（从大到小）
     * <p>
     * 返回有序集合中指定成员的排名，按分数值递减排序
     * </p>
     *
     * @param key   有序集合键
     * @param value 成员值
     * @param <V>   元素类型
     * @return 排名（0表示第一名），如果成员不存在返回null
     */
    public <V> Integer zRevRank(String key, V value) {
        RScoredSortedSet<V> sortedSet = redissonClient.getScoredSortedSet(key);
        return sortedSet.revRank(value);
    }
    
    /**
     * 按分数范围删除有序集合成员
     * <p>
     * 移除有序集合中指定分数区间内的所有成员
     * </p>
     *
     * @param key 有序集合键
     * @param min 最小分数（包含）
     * @param max 最大分数（包含）
     * @return 被移除成员的数量
     */
    public int removeRangeByScore(String key, double min, double max) {
        RScoredSortedSet<Object> sortedSet = redissonClient.getScoredSortedSet(key);
        return sortedSet.removeRangeByScore(min, true, max, true);
    }
    
    /**
     * 按分数范围统计有序集合成员数量
     * <p>
     * 返回有序集合中指定分数区间内的成员数量
     * </p>
     *
     * @param key 有序集合键
     * @param min 最小分数（包含）
     * @param max 最大分数（包含）
     * @return 指定分数区间内的成员数量
     */
    public Long count(String key, double min, double max) {
        RScoredSortedSet<Object> sortedSet = redissonClient.getScoredSortedSet(key);
        return (long) sortedSet.count(min, true, max, true);
    }
    
    /**
     * 向有序集合添加成员（使用分数作为成员值）
     * <p>
     * 向有序集合添加一个成员，分数和成员值相同
     * 适用场景：时间戳作为分数和成员值的滑动窗口限流
     * </p>
     *
     * @param key   有序集合键
     * @param score 分数（同时作为成员值）
     * @param value 成员值
     * @return 是否添加成功
     */
    public boolean add(String key, double score, String value) {
        RScoredSortedSet<String> sortedSet = redissonClient.getScoredSortedSet(key);
        return sortedSet.add(score, value);
    }

    // ================ 原子操作 ================
    
    /**
     * 获取原子长整型对象
     * <p>
     * RAtomicLong是Redisson对Redis原子操作的封装，支持分布式环境下的原子递增递减。
     * 适用场景：分布式计数器、ID生成器等
     * </p>
     *
     * @param key 键
     * @return RAtomicLong实例
     */
    public RAtomicLong getAtomicLong(String key) {
        return redissonClient.getAtomicLong(key);
    }
    
    /**
     * 递增操作
     * <p>
     * 将key中储存的数字值增加指定的增量值
     * </p>
     *
     * @param key   键
     * @param delta 增量值
     * @return 增加后的值
     */
    public long increment(String key, long delta) {
        RAtomicLong atomicLong = redissonClient.getAtomicLong(key);
        return atomicLong.addAndGet(delta);
    }
    
    /**
     * 递增1
     * <p>
     * 将key中储存的数字值增加1
     * </p>
     *
     * @param key 键
     * @return 增加后的值
     */
    public long increment(String key) {
        return increment(key, 1);
    }
    
    /**
     * 递减操作
     * <p>
     * 将key中储存的数字值减少指定的减量值
     * </p>
     *
     * @param key   键
     * @param delta 减量值
     * @return 减少后的值
     */
    public long decrement(String key, long delta) {
        RAtomicLong atomicLong = redissonClient.getAtomicLong(key);
        return atomicLong.addAndGet(-delta);
    }
    
    /**
     * 递减1
     * <p>
     * 将key中储存的数字值减少1
     * </p>
     *
     * @param key 键
     * @return 减少后的值
     */
    public long decrement(String key) {
        return decrement(key, 1);
    }

    // ================ 队列操作 ================
    
    /**
     * 获取队列对象
     * <p>
     * RQueue是Redisson对队列的封装，实现了Java的Queue接口。
     * 适用场景：消息队列、任务队列等
     * </p>
     *
     * @param key 键
     * @param <V> 元素类型
     * @return RQueue实例
     */
    public <V> RQueue<V> getQueue(String key) {
        return redissonClient.getQueue(key);
    }
    
    /**
     * 获取阻塞队列对象
     * <p>
     * RBlockingQueue是Redisson对阻塞队列的封装，实现了Java的BlockingQueue接口。
     * 适用场景：生产者-消费者模式、任务调度等
     * </p>
     *
     * @param key 键
     * @param <V> 元素类型
     * @return RBlockingQueue实例
     */
    public <V> RBlockingQueue<V> getBlockingQueue(String key) {
        return redissonClient.getBlockingQueue(key);
    }
    
    /**
     * 获取延迟队列对象
     * <p>
     * RDelayedQueue是Redisson对延迟队列的封装，元素只有在延迟时间到期后才能被消费。
     * 适用场景：延时任务、定时提醒等
     * </p>
     *
     * @param queue 阻塞队列
     * @param <V>   元素类型
     * @return RDelayedQueue实例
     */
    public <V> RDelayedQueue<V> getDelayedQueue(RBlockingQueue<V> queue) {
        return redissonClient.getDelayedQueue(queue);
    }
    
    /**
     * 向队列添加元素
     * <p>
     * 将元素添加到队列尾部
     * </p>
     *
     * @param key   队列键
     * @param value 要添加的元素
     * @param <V>   元素类型
     * @return 是否添加成功
     */
    public <V> boolean queueOffer(String key, V value) {
        RQueue<V> queue = redissonClient.getQueue(key);
        return queue.offer(value);
    }
    
    /**
     * 从队列获取并移除元素
     * <p>
     * 获取并移除队列头部的元素
     * </p>
     *
     * @param key 队列键
     * @param <V> 元素类型
     * @return 队列头部的元素，如果队列为空返回null
     */
    public <V> V queuePoll(String key) {
        RQueue<V> queue = redissonClient.getQueue(key);
        return queue.poll();
    }
    
    /**
     * 查看队列头部元素但不移除
     * <p>
     * 获取但不移除队列头部的元素
     * </p>
     *
     * @param key 队列键
     * @param <V> 元素类型
     * @return 队列头部的元素，如果队列为空返回null
     */
    public <V> V queuePeek(String key) {
        RQueue<V> queue = redissonClient.getQueue(key);
        return queue.peek();
    }

    // ================ HyperLogLog操作 ================
    
    /**
     * 获取HyperLogLog对象
     * <p>
     * RHyperLogLog是Redisson对Redis HyperLogLog类型的封装，用于基数统计。
     * 适用场景：UV统计、独立访客统计等，占用空间小但有一定误差
     * </p>
     *
     * @param key 键
     * @param <V> 元素类型
     * @return RHyperLogLog实例
     */
    public <V> RHyperLogLog<V> getHyperLogLog(String key) {
        return redissonClient.getHyperLogLog(key);
    }
    
    /**
     * 添加元素到HyperLogLog
     * <p>
     * 将一个或多个元素添加到HyperLogLog中
     * </p>
     *
     * @param key    HyperLogLog键
     * @param values 要添加的元素
     * @param <V>    元素类型
     * @return 是否添加成功
     */
    @SafeVarargs
    public final <V> boolean pfAdd(String key, V... values) {
        RHyperLogLog<V> hyperLogLog = redissonClient.getHyperLogLog(key);
        return hyperLogLog.addAll(Arrays.asList(values));
    }
    
    /**
     * 获取HyperLogLog的基数估算值
     * <p>
     * 返回HyperLogLog的基数估算值
     * </p>
     *
     * @param key HyperLogLog键
     * @return 基数估算值
     */
    public long pfCount(String key) {
        RHyperLogLog<Object> hyperLogLog = redissonClient.getHyperLogLog(key);
        return hyperLogLog.count();
    }

    // ================ BitSet（位图）操作 ================
    
    /**
     * 获取位图对象
     * <p>
     * RBitSet是Redisson对Redis BitMap类型的封装。
     * 适用场景：用户签到、在线状态、权限管理等
     * </p>
     *
     * @param key 键
     * @return RBitSet实例
     */
    public RBitSet getBitSet(String key) {
        return redissonClient.getBitSet(key);
    }
    
    /**
     * 设置位图指定位置的值
     * <p>
     * 对key所储存的字符串值，设置或清除指定偏移量上的位
     * </p>
     *
     * @param key    位图键
     * @param offset 偏移量
     * @param value  值（true表示1，false表示0）
     * @return 指定偏移量原来储存的位
     */
    public boolean setBit(String key, long offset, boolean value) {
        RBitSet bitSet = redissonClient.getBitSet(key);
        boolean oldValue = bitSet.get(offset);
        bitSet.set(offset, value);
        return oldValue;
    }
    
    /**
     * 获取位图指定位置的值
     * <p>
     * 对key所储存的字符串值，获取指定偏移量上的位
     * </p>
     *
     * @param key    位图键
     * @param offset 偏移量
     * @return 位的值（true表示1，false表示0）
     */
    public boolean getBit(String key, long offset) {
        RBitSet bitSet = redissonClient.getBitSet(key);
        return bitSet.get(offset);
    }
    
    /**
     * 统计位图中值为1的位数量
     * <p>
     * 计算给定位图中，被设置为1的位的数量
     * </p>
     *
     * @param key 位图键
     * @return 值为1的位数量
     */
    public long bitCount(String key) {
        RBitSet bitSet = redissonClient.getBitSet(key);
        return bitSet.cardinality();
    }

    // ================ 地理位置操作 ================
    
    /**
     * 获取地理位置对象
     * <p>
     * RGeo是Redisson对Redis Geo类型的封装，用于存储地理位置信息。
     * 适用场景：附近的人、附近的商家、距离计算等
     * </p>
     *
     * @param key 键
     * @param <V> 元素类型
     * @return RGeo实例
     */
    public <V> RGeo<V> getGeo(String key) {
        return redissonClient.getGeo(key);
    }
    
    /**
     * 添加地理位置
     * <p>
     * 将指定的地理空间位置（经度、纬度、名称）添加到指定的key中
     * </p>
     *
     * @param key       地理位置键
     * @param longitude 经度
     * @param latitude  纬度
     * @param member    位置名称
     * @param <V>       元素类型
     * @return 添加的位置数量
     */
    public <V> long geoAdd(String key, double longitude, double latitude, V member) {
        RGeo<V> geo = redissonClient.getGeo(key);
        return geo.add(longitude, latitude, member);
    }
    
    /**
     * 获取两个位置之间的距离
     * <p>
     * 返回两个给定位置之间的距离
     * </p>
     *
     * @param key     地理位置键
     * @param member1 位置1
     * @param member2 位置2
     * @param unit    距离单位
     * @param <V>     元素类型
     * @return 两个位置之间的距离
     */
    public <V> Double geoDist(String key, V member1, V member2, GeoUnit unit) {
        RGeo<V> geo = redissonClient.getGeo(key);
        return geo.dist(member1, member2, unit);
    }
    
    /**
     * 获取指定位置的坐标
     * <p>
     * 从key里返回所有给定位置元素的位置（经度和纬度）
     * </p>
     *
     * @param key     地理位置键
     * @param members 位置名称
     * @param <V>     元素类型
     * @return 位置坐标的Map
     */
    @SafeVarargs
    public final <V> Map<V, GeoPosition> geoPos(String key, V... members) {
        RGeo<V> geo = redissonClient.getGeo(key);
        return geo.pos(members);
    }
    
    /**
     * 查找指定范围内的位置
     * <p>
     * 以给定的经纬度为中心，返回键包含的位置元素当中，与中心的距离不超过给定最大距离的所有位置元素
     * </p>
     *
     * @param key       地理位置键
     * @param longitude 中心经度
     * @param latitude  中心纬度
     * @param radius    半径
     * @param unit      距离单位
     * @param <V>       元素类型
     * @return 符合条件的位置列表
     */
    public <V> List<V> geoRadius(String key, double longitude, double latitude, double radius, GeoUnit unit) {
        RGeo<V> geo = redissonClient.getGeo(key);
        return geo.radius(longitude, latitude, radius, unit);
    }

    // ================ 布隆过滤器相关方法 ================
    
    /**
     * 创建布隆过滤器
     *
     * @param name               布隆过滤器名称
     * @param expectedInsertions 预计插入的元素数量
     * @param falseProbability   误判率
     * @return 布隆过滤器实例
     */
    public RBloomFilter<Object> createBloomFilter(String name, long expectedInsertions, double falseProbability) {
        // 创建布隆过滤器实例
        RBloomFilter<Object> bloomFilter = redissonClient.getBloomFilter(name);
        // 初始化布隆过滤器：预计插入数量 + 期望误判率
        bloomFilter.tryInit(expectedInsertions, falseProbability);
        bloomFilterCache.put(name, bloomFilter);
        log.info("创建布隆过滤器: {}, 预计插入元素数: {}, 误判率: {}", name, expectedInsertions, falseProbability);
        return bloomFilter;
    }

    /**
     * 获取布隆过滤器
     *
     * @param name 布隆过滤器名称
     * @return 布隆过滤器实例，如果不存在则返回null
     */
    public RBloomFilter<Object> getBloomFilter(String name) {
        // 先从缓存中获取
        RBloomFilter<Object> bloomFilter = bloomFilterCache.get(name);
        if (bloomFilter != null) {
            return bloomFilter;
        }

        // 缓存中不存在，从Redisson获取
        bloomFilter = redissonClient.getBloomFilter(name);
        if (bloomFilter.isExists()) {
            bloomFilterCache.put(name, bloomFilter);
            return bloomFilter;
        }

        return null;
    }

    /**
     * 获取或创建布隆过滤器
     *
     * @param name               布隆过滤器名称
     * @param expectedInsertions 预计插入的元素数量
     * @param falseProbability   误判率
     * @return 布隆过滤器实例
     */
    public RBloomFilter<Object> getOrCreateBloomFilter(String name, long expectedInsertions, double falseProbability) {
        RBloomFilter<Object> bloomFilter = getBloomFilter(name);
        if (ObjectUtils.isEmpty(bloomFilter)) {
            return createBloomFilter(name, expectedInsertions, falseProbability);
        }
        return bloomFilter;
    }

    /**
     * 添加元素到布隆过滤器
     *
     * @param name  布隆过滤器名称
     * @param value 要添加的元素
     * @return 是否添加成功
     */
    public boolean addToBloomFilter(String name, Object value) {
        RBloomFilter<Object> bloomFilter = getBloomFilter(name);
        if (bloomFilter == null) {
            log.warn("布隆过滤器不存在: {}", name);
            return false;
        }
        return bloomFilter.add(value);
    }

    /**
     * 检查元素是否可能存在于布隆过滤器中
     *
     * @param name  布隆过滤器名称
     * @param value 要检查的元素
     * @return 如果可能存在返回true，如果一定不存在返回false
     */
    public boolean containsInBloomFilter(String name, Object value) {
        RBloomFilter<Object> bloomFilter = getBloomFilter(name);
        if (bloomFilter == null) {
            log.warn("布隆过滤器不存在: {}", name);
            return false;
        }
        return bloomFilter.contains(value);
    }

    /**
     * 获取布隆过滤器中元素的数量
     *
     * @param name 布隆过滤器名称
     * @return 元素数量
     */
    public long countBloomFilter(String name) {
        RBloomFilter<Object> bloomFilter = getBloomFilter(name);
        if (bloomFilter == null) {
            log.warn("布隆过滤器不存在: {}", name);
            return 0;
        }
        return bloomFilter.count();
    }

    /**
     * 删除布隆过滤器
     *
     * @param name 布隆过滤器名称
     * @return 是否删除成功
     */
    public boolean deleteBloomFilter(String name) {
        RBloomFilter<Object> bloomFilter = getBloomFilter(name);
        if (bloomFilter == null) {
            return false;
        }
        bloomFilterCache.remove(name);
        return bloomFilter.delete();
    }

    // ================ 通用操作 ================
    
    /**
     * 设置键的过期时间
     * <p>
     * 为给定key设置过期时间
     * </p>
     *
     * @param key      键
     * @param timeout  过期时间
     * @param timeUnit 时间单位
     * @return 是否设置成功
     */
    public boolean expire(String key, long timeout, TimeUnit timeUnit) {
        RBucket<Object> bucket = redissonClient.getBucket(key);
        return bucket.expire(timeout, timeUnit);
    }
    
    /**
     * 移除键的过期时间
     * <p>
     * 移除给定key的过期时间，使其永久有效
     * </p>
     *
     * @param key 键
     * @return 是否移除成功
     */
    public boolean persist(String key) {
        RBucket<Object> bucket = redissonClient.getBucket(key);
        return bucket.clearExpire();
    }
    
    /**
     * 获取键的剩余过期时间
     * <p>
     * 以毫秒为单位返回key的剩余过期时间
     * </p>
     *
     * @param key 键
     * @return 剩余过期时间（毫秒），-1表示永久有效，-2表示键不存在
     */
    public long getExpire(String key) {
        RBucket<Object> bucket = redissonClient.getBucket(key);
        return bucket.remainTimeToLive();
    }
    
    /**
     * 删除多个键
     * <p>
     * 删除一个或多个键
     * </p>
     *
     * @param keys 要删除的键
     * @return 成功删除的键数量
     */
    public long deleteKeys(String... keys) {
        return redissonClient.getKeys().delete(keys);
    }
    
    /**
     * 检查多个键是否存在
     * <p>
     * 检查给定的键是否存在
     * </p>
     *
     * @param keys 要检查的键
     * @return 存在的键数量
     */
    public long countExistingKeys(String... keys) {
        return redissonClient.getKeys().countExists(keys);
    }

    // ================ Topic（发布/订阅）操作 ================
    
    /**
     * 获取主题对象
     * <p>
     * RTopic是Redisson对Redis发布/订阅功能的封装，支持消息的发布和订阅。
     * 适用场景：实时通知、消息广播、事件驱动架构等
     * </p>
     *
     * @param topicName 主题名称
     * @return RTopic实例
     */
    public RTopic getTopic(String topicName) {
        return redissonClient.getTopic(topicName);
    }
    
    /**
     * 发布消息到主题
     * <p>
     * 向指定主题发布消息，所有订阅该主题的客户端都会收到消息
     * </p>
     *
     * @param topicName 主题名称
     * @param message   要发布的消息
     * @return 接收到消息的订阅者数量
     */
    public long publish(String topicName, Object message) {
        try {
            RTopic topic = getTopic(topicName);
            long subscriberCount = topic.publish(message);
            log.debug("Published message to topic: {}, subscriber count: {}", topicName, subscriberCount);
            return subscriberCount;
        } catch (Exception e) {
            log.error("Failed to publish message to topic: {}", topicName, e);
            throw new RuntimeException("Failed to publish message", e);
        }
    }
    
    /**
     * 订阅主题消息
     * <p>
     * 订阅指定主题的消息，当有消息发布时会触发监听器
     * 注意：此方法订阅Object类型的消息，如需指定消息类型请使用带messageClass参数的重载方法
     * </p>
     *
     * @param topicName 主题名称
     * @param listener  消息监听器
     * @param <M>       消息类型
     * @return 监听器ID，用于取消订阅
     */
    public <M> int subscribe(String topicName, MessageListener<M> listener) {
        try {
            RTopic topic = getTopic(topicName);
            int listenerId = topic.addListener(Object.class, listener);
            log.debug("Subscribed to topic: {}, listener ID: {}", topicName, listenerId);
            return listenerId;
        } catch (Exception e) {
            log.error("Failed to subscribe to topic: {}", topicName, e);
            throw new RuntimeException("Failed to subscribe to topic", e);
        }
    }
    
    /**
     * 订阅主题消息（指定消息类型）
     * <p>
     * 订阅指定主题的特定类型消息
     * </p>
     *
     * @param topicName    主题名称
     * @param messageClass 消息类型
     * @param listener     消息监听器
     * @param <M>          消息类型
     * @return 监听器ID，用于取消订阅
     */
    public <M> int subscribe(String topicName, Class<M> messageClass, MessageListener<M> listener) {
        try {
            RTopic topic = getTopic(topicName);
            int listenerId = topic.addListener(messageClass, listener);
            log.debug("Subscribed to topic: {} with message class: {}, listener ID: {}", 
                     topicName, messageClass.getSimpleName(), listenerId);
            return listenerId;
        } catch (Exception e) {
            log.error("Failed to subscribe to topic: {} with message class: {}", 
                     topicName, messageClass.getSimpleName(), e);
            throw new RuntimeException("Failed to subscribe to topic", e);
        }
    }
    
    /**
     * 取消订阅
     * <p>
     * 根据监听器ID取消对主题的订阅
     * </p>
     *
     * @param topicName  主题名称
     * @param listenerId 监听器ID
     */
    public void unsubscribe(String topicName, int listenerId) {
        try {
            RTopic topic = getTopic(topicName);
            topic.removeListener(listenerId);
            log.debug("Unsubscribed from topic: {}, listener ID: {}", topicName, listenerId);
        } catch (Exception e) {
            log.error("Failed to unsubscribe from topic: {}, listener ID: {}", topicName, listenerId, e);
            throw new RuntimeException("Failed to unsubscribe from topic", e);
        }
    }
    
    /**
     * 取消所有订阅
     * <p>
     * 取消对指定主题的所有订阅
     * </p>
     *
     * @param topicName 主题名称
     */
    public void unsubscribeAll(String topicName) {
        try {
            RTopic topic = getTopic(topicName);
            topic.removeAllListeners();
            log.debug("Unsubscribed all listeners from topic: {}", topicName);
        } catch (Exception e) {
            log.error("Failed to unsubscribe all listeners from topic: {}", topicName, e);
            throw new RuntimeException("Failed to unsubscribe all listeners", e);
        }
    }
    
    /**
     * 获取模式主题对象
     * <p>
     * RPatternTopic支持模式匹配的主题订阅，可以订阅符合特定模式的多个主题
     * 适用场景：需要订阅多个相关主题的场景
     * </p>
     *
     * @param pattern 主题模式（支持通配符）
     * @return RPatternTopic实例
     */
    public RPatternTopic getPatternTopic(String pattern) {
        return redissonClient.getPatternTopic(pattern);
    }
    
    /**
     * 订阅模式主题
     * <p>
     * 订阅符合指定模式的所有主题
     * </p>
     *
     * @param pattern  主题模式（支持通配符，如 "news.*"）
     * @param listener 消息监听器
     * @param <M>      消息类型
     * @return 监听器ID，用于取消订阅
     */
    public <M> int subscribePattern(String pattern, PatternStatusListener listener) {
        try {
            RPatternTopic patternTopic = getPatternTopic(pattern);
            int listenerId = patternTopic.addListener(listener);
            log.debug("Subscribed to pattern topic: {}, listener ID: {}", pattern, listenerId);
            return listenerId;
        } catch (Exception e) {
            log.error("Failed to subscribe to pattern topic: {}", pattern, e);
            throw new RuntimeException("Failed to subscribe to pattern topic", e);
        }
    }
    
    /**
     * 订阅模式主题（指定消息类型）
     * <p>
     * 订阅符合指定模式的所有主题的特定类型消息
     * </p>
     *
     * @param pattern      主题模式（支持通配符）
     * @param messageClass 消息类型
     * @param listener     消息监听器
     * @param <M>          消息类型
     * @return 监听器ID，用于取消订阅
     */
    public <M> int subscribePattern(String pattern, Class<M> messageClass, PatternMessageListener<M> listener) {
        try {
            RPatternTopic patternTopic = getPatternTopic(pattern);
            int listenerId = patternTopic.addListener(messageClass, listener);
            log.debug("Subscribed to pattern topic: {} with message class: {}, listener ID: {}", 
                     pattern, messageClass.getSimpleName(), listenerId);
            return listenerId;
        } catch (Exception e) {
            log.error("Failed to subscribe to pattern topic: {} with message class: {}", 
                     pattern, messageClass.getSimpleName(), e);
            throw new RuntimeException("Failed to subscribe to pattern topic", e);
        }
    }
    
    /**
     * 取消模式主题订阅
     * <p>
     * 根据监听器ID取消对模式主题的订阅
     * </p>
     *
     * @param pattern    主题模式
     * @param listenerId 监听器ID
     */
    public void unsubscribePattern(String pattern, int listenerId) {
        try {
            RPatternTopic patternTopic = getPatternTopic(pattern);
            patternTopic.removeListener(listenerId);
            log.debug("Unsubscribed from pattern topic: {}, listener ID: {}", pattern, listenerId);
        } catch (Exception e) {
            log.error("Failed to unsubscribe from pattern topic: {}, listener ID: {}", pattern, listenerId, e);
            throw new RuntimeException("Failed to unsubscribe from pattern topic", e);
        }
    }
    
    /**
     * 取消模式主题所有订阅
     * <p>
     * 取消对指定模式主题的所有订阅
     * </p>
     *
     * @param pattern 主题模式
     */
    public void unsubscribePatternAll(String pattern) {
        try {
            RPatternTopic patternTopic = getPatternTopic(pattern);
            patternTopic.removeAllListeners();
            log.debug("Unsubscribed all listeners from pattern topic: {}", pattern);
        } catch (Exception e) {
            log.error("Failed to unsubscribe all listeners from pattern topic: {}", pattern, e);
            throw new RuntimeException("Failed to unsubscribe all listeners", e);
        }
    }
    
    /**
     * 获取可靠主题对象
     * <p>
     * RReliableTopic提供可靠的消息传递，确保消息不会丢失
     * 适用场景：对消息可靠性要求较高的场景
     * </p>
     *
     * @param topicName 主题名称
     * @return RReliableTopic实例
     */
    public RReliableTopic getReliableTopic(String topicName) {
        return redissonClient.getReliableTopic(topicName);
    }
    
    /**
     * 发布可靠消息
     * <p>
     * 向可靠主题发布消息，保证消息的可靠传递
     * </p>
     *
     * @param topicName 主题名称
     * @param message   要发布的消息
     * @return 接收到消息的订阅者数量
     */
    public long publishReliable(String topicName, Object message) {
        try {
            RReliableTopic reliableTopic = getReliableTopic(topicName);
            long subscriberCount = reliableTopic.publish(message);
            log.debug("Published reliable message to topic: {}, subscriber count: {}", topicName, subscriberCount);
            return subscriberCount;
        } catch (Exception e) {
            log.error("Failed to publish reliable message to topic: {}", topicName, e);
            throw new RuntimeException("Failed to publish reliable message", e);
        }
    }

    /**
     * 订阅可靠主题消息（指定消息类型）
     * <p>
     * 订阅可靠主题的特定类型消息
     * </p>
     *
     * @param topicName    主题名称
     * @param messageClass 消息类型
     * @param listener     消息监听器
     * @param <M>          消息类型
     * @return 监听器ID，用于取消订阅
     */
    public <M> String subscribeReliable(String topicName, Class<M> messageClass, MessageListener<M> listener) {
        try {
            RReliableTopic reliableTopic = getReliableTopic(topicName);
            String listenerId = reliableTopic.addListener(messageClass, listener);
            log.debug("Subscribed to reliable topic: {} with message class: {}, listener ID: {}", 
                     topicName, messageClass.getSimpleName(), listenerId);
            return listenerId;
        } catch (Exception e) {
            log.error("Failed to subscribe to reliable topic: {} with message class: {}", 
                     topicName, messageClass.getSimpleName(), e);
            throw new RuntimeException("Failed to subscribe to reliable topic", e);
        }
    }
    
    /**
     * 取消可靠主题订阅
     * <p>
     * 根据监听器ID取消对可靠主题的订阅
     * </p>
     *
     * @param topicName  主题名称
     * @param listenerId 监听器ID
     */
    public void unsubscribeReliable(String topicName, int listenerId) {
        try {
            RReliableTopic reliableTopic = getReliableTopic(topicName);
            reliableTopic.removeListener(listenerId);
            log.debug("Unsubscribed from reliable topic: {}, listener ID: {}", topicName, listenerId);
        } catch (Exception e) {
            log.error("Failed to unsubscribe from reliable topic: {}, listener ID: {}", topicName, listenerId, e);
            throw new RuntimeException("Failed to unsubscribe from reliable topic", e);
        }
    }
    
    /**
     * 取消可靠主题所有订阅
     * <p>
     * 取消对指定可靠主题的所有订阅
     * </p>
     *
     * @param topicName 主题名称
     */
    public void unsubscribeReliableAll(String topicName) {
        try {
            RReliableTopic reliableTopic = getReliableTopic(topicName);
            reliableTopic.removeAllListeners();
            log.debug("Unsubscribed all listeners from reliable topic: {}", topicName);
        } catch (Exception e) {
            log.error("Failed to unsubscribe all listeners from reliable topic: {}", topicName, e);
            throw new RuntimeException("Failed to unsubscribe all listeners", e);
        }
    }
}
