package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

/**
 * @ClassName CacheClient
 * @Description redis缓存工具类
 * @Author 12459
 * @Date 2025/5/19 17:34
 **/
@Component
@Slf4j
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, long time, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);
    }

    //设置逻辑过期
    public void setWithLogicalExpire(String key, Object value, long time, TimeUnit timeUnit) {
        //设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        //写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    //设置缓存穿透
    public <R, ID> R queryWithPassThrough(long time, TimeUnit timeUnit,
            String keyPrefix, ID id, Class<R> clazz, Function<ID, R> dbFallback) {
        String key = keyPrefix + id;
        //从redis查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);

        //判断是否存在
        if (StrUtil.isNotBlank(json)) {
            //存在，返回
            return JSONUtil.toBean(json, clazz);
        }

        //判断是否命中为空值
        if (json != null) {
            return null;
        }

        //不存在，根据id查询数据库
        R r = dbFallback.apply(id);

        //数据库不存在，返回错误
        if (r == null) {
            //将空值写入redis，解决缓存穿透
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            //返回错误信息
            return null;
        }

        //数据库存在，写入redis
        this.set(key, r, time, timeUnit);

        return r;
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /*
     * 利用逻辑过期解决缓存击穿
     * */
    public <R, ID> R queryWithLogicalExpire( long time, TimeUnit timeUnit,
            String keyPrefix, ID id, Class<R> clazz, Function<ID, R> dbFallback) {
        //从redis查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(keyPrefix + id);

        //判断是否存在
        if (StrUtil.isBlank(json)) {
            //存在，返回
            return null;
        }

        //命中，把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), clazz);
        LocalDateTime expireTime = redisData.getExpireTime();

        //判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            return r;
        }

        //获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean lock = tryLock(lockKey);

        //判断是否获取锁成功
        if (lock){
            //成功,开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //重建缓存
                    R r1 = dbFallback.apply(id);
                    this.setWithLogicalExpire(keyPrefix + id, JSONUtil.toJsonStr(r1), time, timeUnit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    //释放锁
                    unlock(lockKey);
                }

            });

        }

        //失败，返回过期的商铺信息
        return r;
    }

    /*
     * 获取锁
     * */
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /*
     * 释放锁
     * */
    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }
}
