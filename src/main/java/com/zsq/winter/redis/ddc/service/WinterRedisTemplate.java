package com.zsq.winter.redis.ddc.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Winter Redis 模板类
 * <p>
 * 基于 Spring Data Redis 的 RedisTemplate 封装的 Redis 操作工具类。
 * 提供了对 Redis 各种数据类型的便捷操作方法，包括 String、Hash、List、Set、ZSet 等。
 * 所有方法都包含完整的异常处理和日志记录。
 * </p>
 *
 * <p>适用场景：</p>
 * <ul>
 *   <li>缓存数据存储和读取</li>
 *   <li>分布式锁实现</li>
 *   <li>计数器功能</li>
 *   <li>排行榜系统</li>
 *   <li>消息队列</li>
 * </ul>
 *
 * @author Winter
 * @since 1.0.0
 */
@Slf4j
public class WinterRedisTemplate {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ValueOperations<String, Object> valueOperations;
    private final HashOperations<String, String, Object> hashOperations;
    private final ListOperations<String, Object> listOperations;
    private final SetOperations<String, Object> setOperations;
    private final ZSetOperations<String, Object> zSetOperations;

    /**
     * 构造函数
     *
     * @param redisTemplate Redis模板对象
     */
    public WinterRedisTemplate(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.valueOperations = redisTemplate.opsForValue();
        this.hashOperations = redisTemplate.opsForHash();
        this.listOperations = redisTemplate.opsForList();
        this.setOperations = redisTemplate.opsForSet();
        this.zSetOperations = redisTemplate.opsForZSet();
    }

    // ================ 通用操作 ================

    /**
     * 构建键字符串，将键列表中的元素使用指定分隔符连接
     *
     * @param keyList        键列表，包含需要连接的字符串元素
     * @param splitCharacter 分隔符，用于连接键列表中的元素
     * @return 返回使用分隔符连接后的键字符串
     */
    public String buildKey(List<String> keyList, String splitCharacter) {
        return String.join(splitCharacter, keyList);
    }



    /**
     * 检查键是否存在
     * <p>
     * 检查指定的键是否在 Redis 中存在
     * </p>
     *
     * @param key 键名
     * @return 如果键存在返回 true，否则返回 false
     */
    public boolean hasKey(String key) {
        try {
            Boolean result = redisTemplate.hasKey(key);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.error("Failed to check if key exists: {}", key, e);
            return false;
        }
    }

    /**
     * 删除键
     * <p>
     * 删除指定的键及其对应的值
     * </p>
     *
     * @param key 键名
     * @return 删除成功返回 true，否则返回 false
     */
    public boolean delete(String key) {
        try {
            Boolean result = redisTemplate.delete(key);
            log.debug("Deleted key: {}, result: {}", key, result);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.error("Failed to delete key: {}", key, e);
            return false;
        }
    }

    /**
     * 批量删除键
     * <p>
     * 批量删除多个键及其对应的值
     * </p>
     *
     * @param keys 键名集合
     * @return 删除的键数量
     */
    public long delete(Collection<String> keys) {
        try {
            if (CollectionUtils.isEmpty(keys)) {
                return 0;
            }
            Long result = redisTemplate.delete(keys);
            log.debug("Deleted keys: {}, count: {}", keys, result);
            return result != null ? result : 0;
        } catch (Exception e) {
            log.error("Failed to delete keys: {}", keys, e);
            return 0;
        }
    }

    /**
     * 设置键的过期时间
     * <p>
     * 为指定的键设置过期时间
     * </p>
     *
     * @param key     键名
     * @param timeout 过期时间
     * @param unit    时间单位
     * @return 设置成功返回 true，否则返回 false
     */
    public boolean expire(String key, long timeout, TimeUnit unit) {
        try {
            Boolean result = redisTemplate.expire(key, timeout, unit);
            log.debug("Set expire for key: {}, timeout: {}, unit: {}, result: {}", key, timeout, unit, result);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.error("Failed to set expire for key: {}", key, e);
            return false;
        }
    }

    /**
     * 设置键的过期时间
     * <p>
     * 为指定的键设置过期时间（使用 Duration）
     * </p>
     *
     * @param key      键名
     * @param duration 过期时间
     * @return 设置成功返回 true，否则返回 false
     */
    public boolean expire(String key, Duration duration) {
        try {
            Boolean result = redisTemplate.expire(key, duration);
            log.debug("Set expire for key: {}, duration: {}, result: {}", key, duration, result);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.error("Failed to set expire for key: {}", key, e);
            return false;
        }
    }

    /**
     * 获取键的剩余过期时间
     * <p>
     * 获取指定键的剩余过期时间（秒）
     * </p>
     *
     * @param key 键名
     * @return 剩余过期时间（秒），-1 表示永不过期，-2 表示键不存在
     */
    public long getExpire(String key) {
        try {
            Long result = redisTemplate.getExpire(key);
            return result != null ? result : -2;
        } catch (Exception e) {
            log.error("Failed to get expire for key: {}", key, e);
            return -2;
        }
    }

    /**
     * 移除键的过期时间
     * <p>
     * 移除指定键的过期时间，使其永不过期
     * </p>
     *
     * @param key 键名
     * @return 移除成功返回 true，否则返回 false
     */
    public boolean persist(String key) {
        try {
            Boolean result = redisTemplate.persist(key);
            log.debug("Persist key: {}, result: {}", key, result);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.error("Failed to persist key: {}", key, e);
            return false;
        }
    }

    // ================ String 操作 ================

    /**
     * 设置字符串值
     * <p>
     * 设置指定键的字符串值
     * </p>
     *
     * @param key   键名
     * @param value 值
     */
    public void set(String key, Object value) {
        try {
            valueOperations.set(key, value);
            log.debug("Set value for key: {}", key);
        } catch (Exception e) {
            log.error("Failed to set value for key: {}", key, e);
            throw new RuntimeException("Failed to set value", e);
        }
    }

    /**
     * 设置字符串值并指定过期时间
     * <p>
     * 设置指定键的字符串值，并设置过期时间
     * </p>
     *
     * @param key     键名
     * @param value   值
     * @param timeout 过期时间
     * @param unit    时间单位
     */
    public void set(String key, Object value, long timeout, TimeUnit unit) {
        try {
            valueOperations.set(key, value, timeout, unit);
            log.debug("Set value for key: {} with timeout: {} {}", key, timeout, unit);
        } catch (Exception e) {
            log.error("Failed to set value with timeout for key: {}", key, e);
            throw new RuntimeException("Failed to set value with timeout", e);
        }
    }

    /**
     * 设置字符串值并指定过期时间
     * <p>
     * 设置指定键的字符串值，并设置过期时间（使用 Duration）
     * </p>
     *
     * @param key      键名
     * @param value    值
     * @param duration 过期时间
     */
    public void set(String key, Object value, Duration duration) {
        try {
            valueOperations.set(key, value, duration);
            log.debug("Set value for key: {} with duration: {}", key, duration);
        } catch (Exception e) {
            log.error("Failed to set value with duration for key: {}", key, e);
            throw new RuntimeException("Failed to set value with duration", e);
        }
    }

    /**
     * 仅当键不存在时设置值
     * <p>
     * 仅当指定键不存在时才设置值
     * </p>
     *
     * @param key   键名
     * @param value 值
     * @return 设置成功返回 true，键已存在返回 false
     */
    public boolean setIfAbsent(String key, Object value) {
        try {
            Boolean result = valueOperations.setIfAbsent(key, value);
            log.debug("Set if absent for key: {}, result: {}", key, result);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.error("Failed to set if absent for key: {}", key, e);
            return false;
        }
    }

    /**
     * 仅当键不存在时设置值并指定过期时间
     * <p>
     * 仅当指定键不存在时才设置值，并设置过期时间
     * </p>
     *
     * @param key     键名
     * @param value   值
     * @param timeout 过期时间
     * @param unit    时间单位
     * @return 设置成功返回 true，键已存在返回 false
     */
    public boolean setIfAbsent(String key, Object value, long timeout, TimeUnit unit) {
        try {
            Boolean result = valueOperations.setIfAbsent(key, value, timeout, unit);
            log.debug("Set if absent for key: {} with timeout: {} {}, result: {}", key, timeout, unit, result);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.error("Failed to set if absent with timeout for key: {}", key, e);
            return false;
        }
    }

    /**
     * 获取字符串值
     * <p>
     * 获取指定键的字符串值
     * </p>
     *
     * @param key 键名
     * @return 值，如果键不存在返回 null
     */
    public Object get(String key) {
        try {
            return valueOperations.get(key);
        } catch (Exception e) {
            log.error("Failed to get value for key: {}", key, e);
            return null;
        }
    }

    /**
     * 获取字符串值并设置新值
     * <p>
     * 获取指定键的当前值，并设置新值
     * </p>
     *
     * @param key   键名
     * @param value 新值
     * @return 旧值，如果键不存在返回 null
     */
    public Object getAndSet(String key, Object value) {
        try {
            Object oldValue = valueOperations.getAndSet(key, value);
            log.debug("Get and set for key: {}, old value exists: {}", key, oldValue != null);
            return oldValue;
        } catch (Exception e) {
            log.error("Failed to get and set for key: {}", key, e);
            return null;
        }
    }

    /**
     * 批量获取字符串值
     * <p>
     * 批量获取多个键的字符串值
     * </p>
     *
     * @param keys 键名集合
     * @return 值列表，对应键的顺序
     */
    public List<Object> multiGet(Collection<String> keys) {
        try {
            if (CollectionUtils.isEmpty(keys)) {
                return new ArrayList<>();
            }
            List<Object> result = valueOperations.multiGet(keys);
            return result != null ? result : new ArrayList<>();
        } catch (Exception e) {
            log.error("Failed to multi get for keys: {}", keys, e);
            return new ArrayList<>();
        }
    }

    /**
     * 递增操作
     * <p>
     * 将指定键的值递增指定的数值
     * </p>
     *
     * @param key   键名
     * @param delta 递增值
     * @return 递增后的值
     */
    public long increment(String key, long delta) {
        try {
            Long result = valueOperations.increment(key, delta);
            log.debug("Increment key: {} by {}, result: {}", key, delta, result);
            return result != null ? result : 0;
        } catch (Exception e) {
            log.error("Failed to increment key: {}", key, e);
            throw new RuntimeException("Failed to increment", e);
        }
    }

    /**
     * 递增操作
     * <p>
     * 将指定键的值递增 1
     * </p>
     *
     * @param key 键名
     * @return 递增后的值
     */
    public long increment(String key) {
        return increment(key, 1);
    }

    /**
     * 递减操作
     * <p>
     * 将指定键的值递减指定的数值
     * </p>
     *
     * @param key   键名
     * @param delta 递减值
     * @return 递减后的值
     */
    public long decrement(String key, long delta) {
        try {
            Long result = valueOperations.decrement(key, delta);
            log.debug("Decrement key: {} by {}, result: {}", key, delta, result);
            return result != null ? result : 0;
        } catch (Exception e) {
            log.error("Failed to decrement key: {}", key, e);
            throw new RuntimeException("Failed to decrement", e);
        }
    }

    /**
     * 递减操作
     * <p>
     * 将指定键的值递减 1
     * </p>
     *
     * @param key 键名
     * @return 递减后的值
     */
    public long decrement(String key) {
        return decrement(key, 1);
    }

    /**
     * 浮点数递增操作
     * <p>
     * 将指定键的浮点数值递增指定的数值
     * </p>
     *
     * @param key   键名
     * @param delta 递增值
     * @return 递增后的值
     */
    public double increment(String key, double delta) {
        try {
            Double result = valueOperations.increment(key, delta);
            log.debug("Increment key: {} by {}, result: {}", key, delta, result);
            return result != null ? result : 0.0;
        } catch (Exception e) {
            log.error("Failed to increment key: {} with double value", key, e);
            throw new RuntimeException("Failed to increment with double", e);
        }
    }

    // ================ Hash 操作 ================

    /**
     * 设置哈希字段值
     * <p>
     * 设置指定键的哈希表中指定字段的值
     * </p>
     *
     * @param key   键名
     * @param field 字段名
     * @param value 值
     */
    public void hashSet(String key, String field, Object value) {
        try {
            hashOperations.put(key, field, value);
            log.debug("Hash set for key: {}, field: {}", key, field);
        } catch (Exception e) {
            log.error("Failed to hash set for key: {}, field: {}", key, field, e);
            throw new RuntimeException("Failed to hash set", e);
        }
    }

    /**
     * 批量设置哈希字段值
     * <p>
     * 批量设置指定键的哈希表中多个字段的值
     * </p>
     *
     * @param key    键名
     * @param values 字段值映射
     */
    public void hashSetAll(String key, Map<String, Object> values) {
        try {
            if (CollectionUtils.isEmpty(values)) {
                return;
            }
            hashOperations.putAll(key, values);
            log.debug("Hash set all for key: {}, fields count: {}", key, values.size());
        } catch (Exception e) {
            log.error("Failed to hash set all for key: {}", key, e);
            throw new RuntimeException("Failed to hash set all", e);
        }
    }

    /**
     * 仅当字段不存在时设置哈希字段值
     * <p>
     * 仅当指定键的哈希表中指定字段不存在时才设置值
     * </p>
     *
     * @param key   键名
     * @param field 字段名
     * @param value 值
     * @return 设置成功返回 true，字段已存在返回 false
     */
    public boolean hashSetIfAbsent(String key, String field, Object value) {
        try {
            Boolean result = hashOperations.putIfAbsent(key, field, value);
            log.debug("Hash set if absent for key: {}, field: {}, result: {}", key, field, result);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.error("Failed to hash set if absent for key: {}, field: {}", key, field, e);
            return false;
        }
    }

    /**
     * 获取哈希字段值
     * <p>
     * 获取指定键的哈希表中指定字段的值
     * </p>
     *
     * @param key   键名
     * @param field 字段名
     * @return 字段值，如果字段不存在返回 null
     */
    public Object hashGet(String key, String field) {
        try {
            return hashOperations.get(key, field);
        } catch (Exception e) {
            log.error("Failed to hash get for key: {}, field: {}", key, field, e);
            return null;
        }
    }

    /**
     * 批量获取哈希字段值
     * <p>
     * 批量获取指定键的哈希表中多个字段的值
     * </p>
     *
     * @param key    键名
     * @param fields 字段名集合
     * @return 字段值列表，对应字段的顺序
     */
    public List<Object> hashMultiGet(String key, Collection<String> fields) {
        try {
            if (CollectionUtils.isEmpty(fields)) {
                return new ArrayList<>();
            }
            List<Object> result = hashOperations.multiGet(key, fields);
            return result != null ? result : new ArrayList<>();
        } catch (Exception e) {
            log.error("Failed to hash multi get for key: {}, fields: {}", key, fields, e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取哈希表所有字段和值
     * <p>
     * 获取指定键的哈希表中所有字段和值
     * </p>
     *
     * @param key 键名
     * @return 字段值映射
     */
    public Map<String, Object> hashGetAll(String key) {
        try {
            Map<String, Object> result = hashOperations.entries(key);
            return result != null ? result : new HashMap<>();
        } catch (Exception e) {
            log.error("Failed to hash get all for key: {}", key, e);
            return new HashMap<>();
        }
    }

    /**
     * 获取哈希表所有字段名
     * <p>
     * 获取指定键的哈希表中所有字段名
     * </p>
     *
     * @param key 键名
     * @return 字段名集合
     */
    public Set<String> hashKeys(String key) {
        try {
            Set<String> result = hashOperations.keys(key);
            return result != null ? result : new HashSet<>();
        } catch (Exception e) {
            log.error("Failed to get hash keys for key: {}", key, e);
            return new HashSet<>();
        }
    }

    /**
     * 获取哈希表所有值
     * <p>
     * 获取指定键的哈希表中所有值
     * </p>
     *
     * @param key 键名
     * @return 值列表
     */
    public List<Object> hashValues(String key) {
        try {
            List<Object> result = hashOperations.values(key);
            return result != null ? result : new ArrayList<>();
        } catch (Exception e) {
            log.error("Failed to get hash values for key: {}", key, e);
            return new ArrayList<>();
        }
    }

    /**
     * 检查哈希字段是否存在
     * <p>
     * 检查指定键的哈希表中指定字段是否存在
     * </p>
     *
     * @param key   键名
     * @param field 字段名
     * @return 如果字段存在返回 true，否则返回 false
     */
    public boolean hashHasKey(String key, String field) {
        try {
            Boolean result = hashOperations.hasKey(key, field);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.error("Failed to check hash key exists for key: {}, field: {}", key, field, e);
            return false;
        }
    }

    /**
     * 删除哈希字段
     * <p>
     * 删除指定键的哈希表中指定字段
     * </p>
     *
     * @param key    键名
     * @param fields 字段名数组
     * @return 删除的字段数量
     */
    public long hashDelete(String key, String... fields) {
        try {
            if (fields == null || fields.length == 0) {
                return 0;
            }
            Long result = hashOperations.delete(key, (Object[]) fields);
            log.debug("Hash delete for key: {}, fields: {}, count: {}", key, Arrays.toString(fields), result);
            return result != null ? result : 0;
        } catch (Exception e) {
            log.error("Failed to hash delete for key: {}, fields: {}", key, Arrays.toString(fields), e);
            return 0;
        }
    }

    /**
     * 获取哈希表字段数量
     * <p>
     * 获取指定键的哈希表中字段的数量
     * </p>
     *
     * @param key 键名
     * @return 字段数量
     */
    public long hashSize(String key) {
        try {
            Long result = hashOperations.size(key);
            return result != null ? result : 0;
        } catch (Exception e) {
            log.error("Failed to get hash size for key: {}", key, e);
            return 0;
        }
    }

    /**
     * 哈希字段值递增
     * <p>
     * 将指定键的哈希表中指定字段的值递增指定的数值
     * </p>
     *
     * @param key   键名
     * @param field 字段名
     * @param delta 递增值
     * @return 递增后的值
     */
    public long hashIncrement(String key, String field, long delta) {
        try {
            Long result = hashOperations.increment(key, field, delta);
            log.debug("Hash increment for key: {}, field: {}, delta: {}, result: {}", key, field, delta, result);
            return result != null ? result : 0;
        } catch (Exception e) {
            log.error("Failed to hash increment for key: {}, field: {}", key, field, e);
            throw new RuntimeException("Failed to hash increment", e);
        }
    }

    /**
     * 哈希字段浮点数值递增
     * <p>
     * 将指定键的哈希表中指定字段的浮点数值递增指定的数值
     * </p>
     *
     * @param key   键名
     * @param field 字段名
     * @param delta 递增值
     * @return 递增后的值
     */
    public double hashIncrement(String key, String field, double delta) {
        try {
            Double result = hashOperations.increment(key, field, delta);
            log.debug("Hash increment for key: {}, field: {}, delta: {}, result: {}", key, field, delta, result);
            return result != null ? result : 0.0;
        } catch (Exception e) {
            log.error("Failed to hash increment with double for key: {}, field: {}", key, field, e);
            throw new RuntimeException("Failed to hash increment with double", e);
        }
    }

    // ================ List 操作 ================

    /**
     * 从列表左侧推入元素
     * <p>
     * 将一个或多个值插入到列表头部
     * </p>
     *
     * @param key    键名
     * @param values 值数组
     * @return 推入后列表的长度
     */
    public long listLeftPush(String key, Object... values) {
        try {
            if (values == null || values.length == 0) {
                return 0;
            }
            Long result = listOperations.leftPushAll(key, values);
            log.debug("List left push for key: {}, values count: {}, result: {}", key, values.length, result);
            return result != null ? result : 0;
        } catch (Exception e) {
            log.error("Failed to list left push for key: {}", key, e);
            throw new RuntimeException("Failed to list left push", e);
        }
    }

    /**
     * 从列表左侧推入单个元素
     * <p>
     * 将一个值插入到列表头部
     * </p>
     *
     * @param key   键名
     * @param value 值
     * @return 推入后列表的长度
     */
    public long listLeftPush(String key, Object value) {
        try {
            Long result = listOperations.leftPush(key, value);
            log.debug("List left push single for key: {}, result: {}", key, result);
            return result != null ? result : 0;
        } catch (Exception e) {
            log.error("Failed to list left push single for key: {}", key, e);
            throw new RuntimeException("Failed to list left push single", e);
        }
    }

    /**
     * 从列表右侧推入元素
     * <p>
     * 将一个或多个值插入到列表尾部
     * </p>
     *
     * @param key    键名
     * @param values 值数组
     * @return 推入后列表的长度
     */
    public long listRightPush(String key, Object... values) {
        try {
            if (values == null || values.length == 0) {
                return 0;
            }
            Long result = listOperations.rightPushAll(key, values);
            log.debug("List right push for key: {}, values count: {}, result: {}", key, values.length, result);
            return result != null ? result : 0;
        } catch (Exception e) {
            log.error("Failed to list right push for key: {}", key, e);
            throw new RuntimeException("Failed to list right push", e);
        }
    }

    /**
     * 从列表右侧推入单个元素
     * <p>
     * 将一个值插入到列表尾部
     * </p>
     *
     * @param key   键名
     * @param value 值
     * @return 推入后列表的长度
     */
    public long listRightPush(String key, Object value) {
        try {
            Long result = listOperations.rightPush(key, value);
            log.debug("List right push single for key: {}, result: {}", key, result);
            return result != null ? result : 0;
        } catch (Exception e) {
            log.error("Failed to list right push single for key: {}", key, e);
            throw new RuntimeException("Failed to list right push single", e);
        }
    }

    /**
     * 从列表左侧弹出元素
     * <p>
     * 移除并返回列表的第一个元素
     * </p>
     *
     * @param key 键名
     * @return 弹出的元素，如果列表为空返回 null
     */
    public Object listLeftPop(String key) {
        try {
            Object result = listOperations.leftPop(key);
            log.debug("List left pop for key: {}, result exists: {}", key, result != null);
            return result;
        } catch (Exception e) {
            log.error("Failed to list left pop for key: {}", key, e);
            return null;
        }
    }

    /**
     * 从列表左侧弹出元素（带超时）
     * <p>
     * 移除并返回列表的第一个元素，如果列表为空则阻塞等待
     * </p>
     *
     * @param key     键名
     * @param timeout 超时时间
     * @param unit    时间单位
     * @return 弹出的元素，如果超时返回 null
     */
    public Object listLeftPop(String key, long timeout, TimeUnit unit) {
        try {
            Object result = listOperations.leftPop(key, timeout, unit);
            log.debug("List left pop with timeout for key: {}, timeout: {} {}, result exists: {}",
                    key, timeout, unit, result != null);
            return result;
        } catch (Exception e) {
            log.error("Failed to list left pop with timeout for key: {}", key, e);
            return null;
        }
    }

    /**
     * 从列表右侧弹出元素
     * <p>
     * 移除并返回列表的最后一个元素
     * </p>
     *
     * @param key 键名
     * @return 弹出的元素，如果列表为空返回 null
     */
    public Object listRightPop(String key) {
        try {
            Object result = listOperations.rightPop(key);
            log.debug("List right pop for key: {}, result exists: {}", key, result != null);
            return result;
        } catch (Exception e) {
            log.error("Failed to list right pop for key: {}", key, e);
            return null;
        }
    }

    /**
     * 从列表右侧弹出元素（带超时）
     * <p>
     * 移除并返回列表的最后一个元素，如果列表为空则阻塞等待
     * </p>
     *
     * @param key     键名
     * @param timeout 超时时间
     * @param unit    时间单位
     * @return 弹出的元素，如果超时返回 null
     */
    public Object listRightPop(String key, long timeout, TimeUnit unit) {
        try {
            Object result = listOperations.rightPop(key, timeout, unit);
            log.debug("List right pop with timeout for key: {}, timeout: {} {}, result exists: {}",
                    key, timeout, unit, result != null);
            return result;
        } catch (Exception e) {
            log.error("Failed to list right pop with timeout for key: {}", key, e);
            return null;
        }
    }

    /**
     * 获取列表指定索引的元素
     * <p>
     * 通过索引获取列表中的元素
     * </p>
     *
     * @param key   键名
     * @param index 索引
     * @return 元素值，如果索引超出范围返回 null
     */
    public Object listIndex(String key, long index) {
        try {
            Object result = listOperations.index(key, index);
            log.debug("List index for key: {}, index: {}, result exists: {}", key, index, result != null);
            return result;
        } catch (Exception e) {
            log.error("Failed to get list index for key: {}, index: {}", key, index, e);
            return null;
        }
    }

    /**
     * 设置列表指定索引的元素值
     * <p>
     * 通过索引设置列表元素的值
     * </p>
     *
     * @param key   键名
     * @param index 索引
     * @param value 新值
     */
    public void listSet(String key, long index, Object value) {
        try {
            listOperations.set(key, index, value);
            log.debug("List set for key: {}, index: {}", key, index);
        } catch (Exception e) {
            log.error("Failed to list set for key: {}, index: {}", key, index, e);
            throw new RuntimeException("Failed to list set", e);
        }
    }

    /**
     * 获取列表指定范围的元素
     * <p>
     * 返回列表中指定区间内的元素
     * </p>
     *
     * @param key   键名
     * @param start 开始位置
     * @param end   结束位置
     * @return 元素列表
     */
    public List<Object> listRange(String key, long start, long end) {
        try {
            List<Object> result = listOperations.range(key, start, end);
            log.debug("List range for key: {}, start: {}, end: {}, size: {}",
                    key, start, end, result != null ? result.size() : 0);
            return result != null ? result : new ArrayList<>();
        } catch (Exception e) {
            log.error("Failed to get list range for key: {}, start: {}, end: {}", key, start, end, e);
            return new ArrayList<>();
        }
    }

    /**
     * 修剪列表
     * <p>
     * 对一个列表进行修剪，让列表只保留指定区间内的元素
     * </p>
     *
     * @param key   键名
     * @param start 开始位置
     * @param end   结束位置
     */
    public void listTrim(String key, long start, long end) {
        try {
            listOperations.trim(key, start, end);
            log.debug("List trim for key: {}, start: {}, end: {}", key, start, end);
        } catch (Exception e) {
            log.error("Failed to list trim for key: {}, start: {}, end: {}", key, start, end, e);
            throw new RuntimeException("Failed to list trim", e);
        }
    }

    /**
     * 获取列表长度
     * <p>
     * 返回列表的长度
     * </p>
     *
     * @param key 键名
     * @return 列表长度
     */
    public long listSize(String key) {
        try {
            Long result = listOperations.size(key);
            return result != null ? result : 0;
        } catch (Exception e) {
            log.error("Failed to get list size for key: {}", key, e);
            return 0;
        }
    }

    /**
     * 移除列表元素
     * <p>
     * 根据参数 count 的值，移除列表中与参数 value 相等的元素
     * </p>
     *
     * @param key   键名
     * @param count 移除的数量
     * @param value 要移除的值
     * @return 被移除元素的数量
     */
    public long listRemove(String key, long count, Object value) {
        try {
            Long result = listOperations.remove(key, count, value);
            log.debug("List remove for key: {}, count: {}, value: {}, result: {}", key, count, value, result);
            return result != null ? result : 0;
        } catch (Exception e) {
            log.error("Failed to list remove for key: {}, count: {}, value: {}", key, count, value, e);
            return 0;
        }
    }

    // ========== Set 操作 ==========

    /**
     * 向Set中添加一个或多个成员
     *
     * @param key    键
     * @param values 值
     * @return 添加成功的成员数量
     */
    public Long setAdd(String key, Object... values) {
        try {
            log.debug("Adding values to set with key: {}", key);
            return redisTemplate.opsForSet().add(key, values);
        } catch (Exception e) {
            log.error("Error adding values to set with key: {}", key, e);
            throw new RuntimeException("Failed to add values to set", e);
        }
    }

    /**
     * 移除Set中的一个或多个成员
     *
     * @param key    键
     * @param values 值
     * @return 移除成功的成员数量
     */
    public Long setRemove(String key, Object... values) {
        try {
            log.debug("Removing values from set with key: {}", key);
            return redisTemplate.opsForSet().remove(key, values);
        } catch (Exception e) {
            log.error("Error removing values from set with key: {}", key, e);
            throw new RuntimeException("Failed to remove values from set", e);
        }
    }

    /**
     * 判断Set中是否包含指定成员
     *
     * @param key   键
     * @param value 值
     * @return 是否包含
     */
    public Boolean setIsMember(String key, Object value) {
        try {
            log.debug("Checking if set contains member with key: {}", key);
            return redisTemplate.opsForSet().isMember(key, value);
        } catch (Exception e) {
            log.error("Error checking set membership with key: {}", key, e);
            throw new RuntimeException("Failed to check set membership", e);
        }
    }

    /**
     * 获取Set中的所有成员
     *
     * @param key 键
     * @return Set中的所有成员
     */
    public Set<Object> setMembers(String key) {
        try {
            log.debug("Getting all members from set with key: {}", key);
            return redisTemplate.opsForSet().members(key);
        } catch (Exception e) {
            log.error("Error getting set members with key: {}", key, e);
            throw new RuntimeException("Failed to get set members", e);
        }
    }

    /**
     * 获取Set的大小
     *
     * @param key 键
     * @return Set的大小
     */
    public Long setSize(String key) {
        try {
            log.debug("Getting set size with key: {}", key);
            return redisTemplate.opsForSet().size(key);
        } catch (Exception e) {
            log.error("Error getting set size with key: {}", key, e);
            throw new RuntimeException("Failed to get set size", e);
        }
    }

    /**
     * 随机获取Set中的一个成员
     *
     * @param key 键
     * @return 随机成员
     */
    public Object setRandomMember(String key) {
        try {
            log.debug("Getting random member from set with key: {}", key);
            return redisTemplate.opsForSet().randomMember(key);
        } catch (Exception e) {
            log.error("Error getting random member from set with key: {}", key, e);
            throw new RuntimeException("Failed to get random member from set", e);
        }
    }

    /**
     * 随机获取Set中的多个成员
     *
     * @param key   键
     * @param count 数量
     * @return 随机成员列表
     */
    public List<Object> setRandomMembers(String key, long count) {
        try {
            log.debug("Getting {} random members from set with key: {}", count, key);
            return redisTemplate.opsForSet().randomMembers(key, count);
        } catch (Exception e) {
            log.error("Error getting random members from set with key: {}", key, e);
            throw new RuntimeException("Failed to get random members from set", e);
        }
    }

    /**
     * 随机获取并移除Set中的一个成员
     *
     * @param key 键
     * @return 被移除的成员
     */
    public Object setPop(String key) {
        try {
            log.debug("Popping member from set with key: {}", key);
            return redisTemplate.opsForSet().pop(key);
        } catch (Exception e) {
            log.error("Error popping member from set with key: {}", key, e);
            throw new RuntimeException("Failed to pop member from set", e);
        }
    }

    /**
     * 随机获取并移除Set中的多个成员
     *
     * @param key   键
     * @param count 数量
     * @return 被移除的成员列表
     */
    public List<Object> setPop(String key, long count) {
        try {
            log.debug("Popping {} members from set with key: {}", count, key);
            return redisTemplate.opsForSet().pop(key, count);
        } catch (Exception e) {
            log.error("Error popping members from set with key: {}", key, e);
            throw new RuntimeException("Failed to pop members from set", e);
        }
    }

    /**
     * 获取两个Set的交集
     *
     * @param key      第一个Set的键
     * @param otherKey 第二个Set的键
     * @return 交集
     */
    public Set<Object> setIntersect(String key, String otherKey) {
        try {
            log.debug("Getting intersection of sets with keys: {} and {}", key, otherKey);
            return redisTemplate.opsForSet().intersect(key, otherKey);
        } catch (Exception e) {
            log.error("Error getting set intersection with keys: {} and {}", key, otherKey, e);
            throw new RuntimeException("Failed to get set intersection", e);
        }
    }

    /**
     * 获取多个Set的交集
     *
     * @param key       第一个Set的键
     * @param otherKeys 其他Set的键
     * @return 交集
     */
    public Set<Object> setIntersect(String key, Collection<String> otherKeys) {
        try {
            log.debug("Getting intersection of sets with key: {} and other keys: {}", key, otherKeys);
            return redisTemplate.opsForSet().intersect(key, otherKeys);
        } catch (Exception e) {
            log.error("Error getting set intersection with key: {} and other keys: {}", key, otherKeys, e);
            throw new RuntimeException("Failed to get set intersection", e);
        }
    }

    /**
     * 获取两个Set的并集
     *
     * @param key      第一个Set的键
     * @param otherKey 第二个Set的键
     * @return 并集
     */
    public Set<Object> setUnion(String key, String otherKey) {
        try {
            log.debug("Getting union of sets with keys: {} and {}", key, otherKey);
            return redisTemplate.opsForSet().union(key, otherKey);
        } catch (Exception e) {
            log.error("Error getting set union with keys: {} and {}", key, otherKey, e);
            throw new RuntimeException("Failed to get set union", e);
        }
    }

    /**
     * 获取多个Set的并集
     *
     * @param key       第一个Set的键
     * @param otherKeys 其他Set的键
     * @return 并集
     */
    public Set<Object> setUnion(String key, Collection<String> otherKeys) {
        try {
            log.debug("Getting union of sets with key: {} and other keys: {}", key, otherKeys);
            return redisTemplate.opsForSet().union(key, otherKeys);
        } catch (Exception e) {
            log.error("Error getting set union with key: {} and other keys: {}", key, otherKeys, e);
            throw new RuntimeException("Failed to get set union", e);
        }
    }

    /**
     * 获取两个Set的差集
     *
     * @param key      第一个Set的键
     * @param otherKey 第二个Set的键
     * @return 差集
     */
    public Set<Object> setDifference(String key, String otherKey) {
        try {
            log.debug("Getting difference of sets with keys: {} and {}", key, otherKey);
            return redisTemplate.opsForSet().difference(key, otherKey);
        } catch (Exception e) {
            log.error("Error getting set difference with keys: {} and {}", key, otherKey, e);
            throw new RuntimeException("Failed to get set difference", e);
        }
    }

    /**
     * 获取多个Set的差集
     *
     * @param key       第一个Set的键
     * @param otherKeys 其他Set的键
     * @return 差集
     */
    public Set<Object> setDifference(String key, Collection<String> otherKeys) {
        try {
            log.debug("Getting difference of sets with key: {} and other keys: {}", key, otherKeys);
            return redisTemplate.opsForSet().difference(key, otherKeys);
        } catch (Exception e) {
            log.error("Error getting set difference with key: {} and other keys: {}", key, otherKeys, e);
            throw new RuntimeException("Failed to get set difference", e);
        }
    }

    /**
     * 计算两个Set的交集并存储到目标键中
     *
     * @param key      第一个Set的键
     * @param otherKey 第二个Set的键
     * @param destKey  目标键
     * @return 交集中的元素数量
     */
    public Long setIntersectAndStore(String key, String otherKey, String destKey) {
        try {
            log.debug("Computing intersection of sets with keys: {} and {}, storing to: {}", key, otherKey, destKey);
            return redisTemplate.opsForSet().intersectAndStore(key, otherKey, destKey);
        } catch (Exception e) {
            log.error("Error computing and storing set intersection with keys: {} and {}", key, otherKey, e);
            throw new RuntimeException("Failed to compute and store set intersection", e);
        }
    }

    /**
     * 计算多个Set的交集并存储到目标键中
     *
     * @param key       第一个Set的键
     * @param otherKeys 其他Set的键
     * @param destKey   目标键
     * @return 交集中的元素数量
     */
    public Long setIntersectAndStore(String key, Collection<String> otherKeys, String destKey) {
        try {
            log.debug("Computing intersection of sets with key: {} and other keys: {}, storing to: {}", key, otherKeys, destKey);
            return redisTemplate.opsForSet().intersectAndStore(key, otherKeys, destKey);
        } catch (Exception e) {
            log.error("Error computing and storing set intersection with key: {} and other keys: {}", key, otherKeys, e);
            throw new RuntimeException("Failed to compute and store set intersection", e);
        }
    }

    /**
     * 计算两个Set的并集并存储到目标键中
     *
     * @param key      第一个Set的键
     * @param otherKey 第二个Set的键
     * @param destKey  目标键
     * @return 并集中的元素数量
     */
    public Long setUnionAndStore(String key, String otherKey, String destKey) {
        try {
            log.debug("Computing union of sets with keys: {} and {}, storing to: {}", key, otherKey, destKey);
            return redisTemplate.opsForSet().unionAndStore(key, otherKey, destKey);
        } catch (Exception e) {
            log.error("Error computing and storing set union with keys: {} and {}", key, otherKey, e);
            throw new RuntimeException("Failed to compute and store set union", e);
        }
    }

    /**
     * 计算多个Set的并集并存储到目标键中
     *
     * @param key       第一个Set的键
     * @param otherKeys 其他Set的键
     * @param destKey   目标键
     * @return 并集中的元素数量
     */
    public Long setUnionAndStore(String key, Collection<String> otherKeys, String destKey) {
        try {
            log.debug("Computing union of sets with key: {} and other keys: {}, storing to: {}", key, otherKeys, destKey);
            return redisTemplate.opsForSet().unionAndStore(key, otherKeys, destKey);
        } catch (Exception e) {
            log.error("Error computing and storing set union with key: {} and other keys: {}", key, otherKeys, e);
            throw new RuntimeException("Failed to compute and store set union", e);
        }
    }

    /**
     * 计算两个Set的差集并存储到目标键中
     *
     * @param key      第一个Set的键
     * @param otherKey 第二个Set的键
     * @param destKey  目标键
     * @return 差集中的元素数量
     */
    public Long setDifferenceAndStore(String key, String otherKey, String destKey) {
        try {
            log.debug("Computing difference of sets with keys: {} and {}, storing to: {}", key, otherKey, destKey);
            return redisTemplate.opsForSet().differenceAndStore(key, otherKey, destKey);
        } catch (Exception e) {
            log.error("Error computing and storing set difference with keys: {} and {}", key, otherKey, e);
            throw new RuntimeException("Failed to compute and store set difference", e);
        }
    }

    /**
     * 计算多个Set的差集并存储到目标键中
     *
     * @param key       第一个Set的键
     * @param otherKeys 其他Set的键
     * @param destKey   目标键
     * @return 差集中的元素数量
     */
    public Long setDifferenceAndStore(String key, Collection<String> otherKeys, String destKey) {
        try {
            log.debug("Computing difference of sets with key: {} and other keys: {}, storing to: {}", key, otherKeys, destKey);
            return redisTemplate.opsForSet().differenceAndStore(key, otherKeys, destKey);
        } catch (Exception e) {
            log.error("Error computing and storing set difference with key: {} and other keys: {}", key, otherKeys, e);
            throw new RuntimeException("Failed to compute and store set difference", e);
        }
    }

    /**
     * 将Set中的成员从源键移动到目标键
     *
     * @param sourceKey 源键
     * @param value     要移动的值
     * @param destKey   目标键
     * @return 移动成功返回true，否则返回false
     */
    public Boolean setMove(String sourceKey, Object value, String destKey) {
        try {
            log.debug("Moving value from set with key: {} to set with key: {}", sourceKey, destKey);
            return redisTemplate.opsForSet().move(sourceKey, value, destKey);
        } catch (Exception e) {
            log.error("Error moving value from set with key: {} to set with key: {}", sourceKey, destKey, e);
            throw new RuntimeException("Failed to move value between sets", e);
        }
    }

    // ========== ZSet 操作 ==========

    /**
     * 向ZSet中添加一个成员
     *
     * @param key   键
     * @param value 值
     * @param score 分数
     * @return 是否添加成功
     */
    public Boolean zSetAdd(String key, Object value, double score) {
        try {
            log.debug("Adding value to zset with key: {}, score: {}", key, score);
            return redisTemplate.opsForZSet().add(key, value, score);
        } catch (Exception e) {
            log.error("Error adding value to zset with key: {}", key, e);
            throw new RuntimeException("Failed to add value to zset", e);
        }
    }

    /**
     * 向ZSet中添加多个成员
     *
     * @param key    键
     * @param tuples 成员和分数的集合
     * @return 添加成功的成员数量
     */
    public Long zSetAdd(String key, Set<ZSetOperations.TypedTuple<Object>> tuples) {
        try {
            log.debug("Adding multiple values to zset with key: {}", key);
            return redisTemplate.opsForZSet().add(key, tuples);
        } catch (Exception e) {
            log.error("Error adding multiple values to zset with key: {}", key, e);
            throw new RuntimeException("Failed to add multiple values to zset", e);
        }
    }

    /**
     * 移除ZSet中的一个或多个成员
     *
     * @param key    键
     * @param values 值
     * @return 移除成功的成员数量
     */
    public Long zSetRemove(String key, Object... values) {
        try {
            log.debug("Removing values from zset with key: {}", key);
            return redisTemplate.opsForZSet().remove(key, values);
        } catch (Exception e) {
            log.error("Error removing values from zset with key: {}", key, e);
            throw new RuntimeException("Failed to remove values from zset", e);
        }
    }

    /**
     * 增加ZSet中成员的分数
     *
     * @param key   键
     * @param value 值
     * @param delta 增量
     * @return 增加后的分数
     */
    public Double zSetIncrementScore(String key, Object value, double delta) {
        try {
            log.debug("Incrementing score for value in zset with key: {}, delta: {}", key, delta);
            return redisTemplate.opsForZSet().incrementScore(key, value, delta);
        } catch (Exception e) {
            log.error("Error incrementing score in zset with key: {}", key, e);
            throw new RuntimeException("Failed to increment score in zset", e);
        }
    }

    /**
     * 获取ZSet中成员的排名（从小到大）
     *
     * @param key   键
     * @param value 值
     * @return 排名（从0开始）
     */
    public Long zSetRank(String key, Object value) {
        try {
            log.debug("Getting rank for value in zset with key: {}", key);
            return redisTemplate.opsForZSet().rank(key, value);
        } catch (Exception e) {
            log.error("Error getting rank in zset with key: {}", key, e);
            throw new RuntimeException("Failed to get rank in zset", e);
        }
    }

    /**
     * 获取ZSet中成员的排名（从大到小）
     *
     * @param key   键
     * @param value 值
     * @return 排名（从0开始）
     */
    public Long zSetReverseRank(String key, Object value) {
        try {
            log.debug("Getting reverse rank for value in zset with key: {}", key);
            return redisTemplate.opsForZSet().reverseRank(key, value);
        } catch (Exception e) {
            log.error("Error getting reverse rank in zset with key: {}", key, e);
            throw new RuntimeException("Failed to get reverse rank in zset", e);
        }
    }

    /**
     * 获取ZSet中成员的分数
     *
     * @param key   键
     * @param value 值
     * @return 分数
     */
    public Double zSetScore(String key, Object value) {
        try {
            log.debug("Getting score for value in zset with key: {}", key);
            return redisTemplate.opsForZSet().score(key, value);
        } catch (Exception e) {
            log.error("Error getting score in zset with key: {}", key, e);
            throw new RuntimeException("Failed to get score in zset", e);
        }
    }

    /**
     * 获取ZSet的大小
     *
     * @param key 键
     * @return ZSet的大小
     */
    public Long zSetSize(String key) {
        try {
            log.debug("Getting zset size with key: {}", key);
            return redisTemplate.opsForZSet().size(key);
        } catch (Exception e) {
            log.error("Error getting zset size with key: {}", key, e);
            throw new RuntimeException("Failed to get zset size", e);
        }
    }

    /**
     * 获取ZSet中指定分数范围内的成员数量
     *
     * @param key 键
     * @param min 最小分数
     * @param max 最大分数
     * @return 成员数量
     */
    public Long zSetCount(String key, double min, double max) {
        try {
            log.debug("Counting members in zset with key: {}, score range: {} - {}", key, min, max);
            return redisTemplate.opsForZSet().count(key, min, max);
        } catch (Exception e) {
            log.error("Error counting members in zset with key: {}", key, e);
            throw new RuntimeException("Failed to count members in zset", e);
        }
    }

    /**
     * 获取ZSet中指定排名范围的成员（从小到大）
     *
     * @param key   键
     * @param start 开始排名
     * @param end   结束排名
     * @return 成员集合
     */
    public Set<Object> zSetRange(String key, long start, long end) {
        try {
            log.debug("Getting range from zset with key: {}, range: {} - {}", key, start, end);
            return redisTemplate.opsForZSet().range(key, start, end);
        } catch (Exception e) {
            log.error("Error getting range from zset with key: {}", key, e);
            throw new RuntimeException("Failed to get range from zset", e);
        }
    }

    /**
     * 获取ZSet中指定排名范围的成员和分数（从小到大）
     *
     * @param key   键
     * @param start 开始排名
     * @param end   结束排名
     * @return 成员和分数的集合
     */
    public Set<ZSetOperations.TypedTuple<Object>> zSetRangeWithScores(String key, long start, long end) {
        try {
            log.debug("Getting range with scores from zset with key: {}, range: {} - {}", key, start, end);
            return redisTemplate.opsForZSet().rangeWithScores(key, start, end);
        } catch (Exception e) {
            log.error("Error getting range with scores from zset with key: {}", key, e);
            throw new RuntimeException("Failed to get range with scores from zset", e);
        }
    }

    /**
     * 获取ZSet中指定分数范围的成员（从小到大）
     *
     * @param key 键
     * @param min 最小分数
     * @param max 最大分数
     * @return 成员集合
     */
    public Set<Object> zSetRangeByScore(String key, double min, double max) {
        try {
            log.debug("Getting range by score from zset with key: {}, score range: {} - {}", key, min, max);
            return redisTemplate.opsForZSet().rangeByScore(key, min, max);
        } catch (Exception e) {
            log.error("Error getting range by score from zset with key: {}", key, e);
            throw new RuntimeException("Failed to get range by score from zset", e);
        }
    }

    /**
     * 获取ZSet中指定分数范围的成员和分数（从小到大）
     *
     * @param key 键
     * @param min 最小分数
     * @param max 最大分数
     * @return 成员和分数的集合
     */
    public Set<ZSetOperations.TypedTuple<Object>> zSetRangeByScoreWithScores(String key, double min, double max) {
        try {
            log.debug("Getting range by score with scores from zset with key: {}, score range: {} - {}", key, min, max);
            return redisTemplate.opsForZSet().rangeByScoreWithScores(key, min, max);
        } catch (Exception e) {
            log.error("Error getting range by score with scores from zset with key: {}", key, e);
            throw new RuntimeException("Failed to get range by score with scores from zset", e);
        }
    }

    /**
     * 获取ZSet中指定排名范围的成员（从大到小）
     *
     * @param key   键
     * @param start 开始排名
     * @param end   结束排名
     * @return 成员集合
     */
    public Set<Object> zSetReverseRange(String key, long start, long end) {
        try {
            log.debug("Getting reverse range from zset with key: {}, range: {} - {}", key, start, end);
            return redisTemplate.opsForZSet().reverseRange(key, start, end);
        } catch (Exception e) {
            log.error("Error getting reverse range from zset with key: {}", key, e);
            throw new RuntimeException("Failed to get reverse range from zset", e);
        }
    }

    /**
     * 获取ZSet中指定排名范围的成员和分数（从大到小）
     *
     * @param key   键
     * @param start 开始排名
     * @param end   结束排名
     * @return 成员和分数的集合
     */
    public Set<ZSetOperations.TypedTuple<Object>> zSetReverseRangeWithScores(String key, long start, long end) {
        try {
            log.debug("Getting reverse range with scores from zset with key: {}, range: {} - {}", key, start, end);
            return redisTemplate.opsForZSet().reverseRangeWithScores(key, start, end);
        } catch (Exception e) {
            log.error("Error getting reverse range with scores from zset with key: {}", key, e);
            throw new RuntimeException("Failed to get reverse range with scores from zset", e);
        }
    }

    /**
     * 获取ZSet中指定分数范围的成员（从大到小）
     *
     * @param key 键
     * @param min 最小分数
     * @param max 最大分数
     * @return 成员集合
     */
    public Set<Object> zSetReverseRangeByScore(String key, double min, double max) {
        try {
            log.debug("Getting reverse range by score from zset with key: {}, score range: {} - {}", key, min, max);
            return redisTemplate.opsForZSet().reverseRangeByScore(key, min, max);
        } catch (Exception e) {
            log.error("Error getting reverse range by score from zset with key: {}", key, e);
            throw new RuntimeException("Failed to get reverse range by score from zset", e);
        }
    }

    /**
     * 移除ZSet中指定排名范围的成员
     *
     * @param key   键
     * @param start 开始排名
     * @param end   结束排名
     * @return 移除的成员数量
     */
    public Long zSetRemoveRange(String key, long start, long end) {
        try {
            log.debug("Removing range from zset with key: {}, range: {} - {}", key, start, end);
            return redisTemplate.opsForZSet().removeRange(key, start, end);
        } catch (Exception e) {
            log.error("Error removing range from zset with key: {}", key, e);
            throw new RuntimeException("Failed to remove range from zset", e);
        }
    }

    /**
     * 删除ZSet中指定分数范围的成员
     *
     * @param key 键
     * @param min 最小分数
     * @param max 最大分数
     * @return 移除的成员数量
     */
    public Long zSetRemoveRangeByScore(String key, double min, double max) {
        try {
            log.debug("Removing range by score from zset with key: {}, score range: {} - {}", key, min, max);
            return redisTemplate.opsForZSet().removeRangeByScore(key, min, max);
        } catch (Exception e) {
            log.error("Error removing range by score from zset with key: {}", key, e);
            throw new RuntimeException("Failed to remove range by score from zset", e);
        }
    }

    /**
     * 计算两个ZSet的并集并存储到目标键中
     *
     * @param key      第一个ZSet的键
     * @param otherKey 第二个ZSet的键
     * @param destKey  目标键
     * @return 并集中的元素数量
     */
    public Long zSetUnionAndStore(String key, String otherKey, String destKey) {
        try {
            log.debug("Computing union of zsets with keys: {} and {}, storing to: {}", key, otherKey, destKey);
            return redisTemplate.opsForZSet().unionAndStore(key, otherKey, destKey);
        } catch (Exception e) {
            log.error("Error computing and storing zset union with keys: {} and {}", key, otherKey, e);
            throw new RuntimeException("Failed to compute and store zset union", e);
        }
    }

    /**
     * 计算多个ZSet的并集并存储到目标键中
     *
     * @param key       第一个ZSet的键
     * @param otherKeys 其他ZSet的键
     * @param destKey   目标键
     * @return 并集中的元素数量
     */
    public Long zSetUnionAndStore(String key, Collection<String> otherKeys, String destKey) {
        try {
            log.debug("Computing union of zsets with key: {} and other keys: {}, storing to: {}", key, otherKeys, destKey);
            return redisTemplate.opsForZSet().unionAndStore(key, otherKeys, destKey);
        } catch (Exception e) {
            log.error("Error computing and storing zset union with key: {} and other keys: {}", key, otherKeys, e);
            throw new RuntimeException("Failed to compute and store zset union", e);
        }
    }

    /**
     * 计算两个ZSet的交集并存储到目标键中
     *
     * @param key      第一个ZSet的键
     * @param otherKey 第二个ZSet的键
     * @param destKey  目标键
     * @return 交集中的元素数量
     */
    public Long zSetIntersectAndStore(String key, String otherKey, String destKey) {
        try {
            log.debug("Computing intersection of zsets with keys: {} and {}, storing to: {}", key, otherKey, destKey);
            return redisTemplate.opsForZSet().intersectAndStore(key, otherKey, destKey);
        } catch (Exception e) {
            log.error("Error computing and storing zset intersection with keys: {} and {}", key, otherKey, e);
            throw new RuntimeException("Failed to compute and store zset intersection", e);
        }
    }

    /**
     * 计算多个ZSet的交集并存储到目标键中
     *
     * @param key       第一个ZSet的键
     * @param otherKeys 其他ZSet的键
     * @param destKey   目标键
     * @return 交集中的元素数量
     */
    public Long zSetIntersectAndStore(String key, Collection<String> otherKeys, String destKey) {
        try {
            log.debug("Computing intersection of zsets with key: {} and other keys: {}, storing to: {}", key, otherKeys, destKey);
            return redisTemplate.opsForZSet().intersectAndStore(key, otherKeys, destKey);
        } catch (Exception e) {
            log.error("Error computing and storing zset intersection with key: {} and other keys: {}", key, otherKeys, e);
            throw new RuntimeException("Failed to compute and store zset intersection", e);
        }
    }

}