package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        //缓存穿透
        //Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_TTL, TimeUnit.MINUTES, CACHE_SHOP_KEY, id, Shop.class, this::getById);
        //互斥锁解决缓存击穿
        Shop shop = cacheClient
                .queryWithLogicalExpire(CACHE_SHOP_TTL, TimeUnit.MINUTES, CACHE_SHOP_KEY, id, Shop.class, this::getById);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }

        return Result.ok(shop);
    }

    /*
     * 查询并解决缓存穿透和缓存击穿
     * *//*
    public Shop queryWithMutex(Long id) {
        //从redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);

        //判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            //存在，返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }

        //判断是否命中为空值
        if (shopJson != null) {
            return null;
        }

        //实现缓存重建
        //获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            boolean isLock = tryLock(lockKey);
            //判断是否获取成功
            if (!isLock) {
                //失败，休眠并重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }

            //不存在，根据id查询数据库
            shop = getById(id);

            //模拟重建的延时
            Thread.sleep(200);

            //数据库不存在，返回错误
            if (shop == null) {
                //将空值写入redis，解决缓存穿透
                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                //返回错误信息
                return null;
            }

            //数据库存在，写入redis
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            //释放互斥锁
            unlock(lockKey);
        }
        return shop;
    }*/

    /*避免递归抛出 StackOverflowError，导致线程崩溃
    public Shop queryWithMutexImproved(Long id) {
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        if (StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        if (shopJson != null) { // 空字符串，缓存穿透保护
            return null;
        }

        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop = null;
        int maxRetries = 10; // 例如，最大重试次数
        int retryCount = 0;

        try {
            while (retryCount < maxRetries) {
                if (tryLock(lockKey)) { // 尝试获取锁
                    try {
                        // 双重检查，防止在等待锁的过程中，其他线程已经重建了缓存
                        shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
                        if (StrUtil.isNotBlank(shopJson)) {
                            return JSONUtil.toBean(shopJson, Shop.class);
                        }
                        if (shopJson != null) {
                            return null;
                        }

                        // 不存在，根据id查询数据库
                        shop = getById(id);
                        Thread.sleep(200); // 模拟耗时操作

                        if (shop == null) {
                            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                            return null;
                        }
                        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
                        return shop; // 成功获取并重建缓存后返回
                    } finally {
                        unlock(lockKey); // 释放锁
                    }
                } else {
                    // 获取锁失败
                    retryCount++;
                    if (retryCount >= maxRetries) {
                        // log.error("Failed to acquire lock after {} retries for shop id: {}", maxRetries, id);
                        // 可以选择抛出异常，或者返回null/降级处理
                        return null; // 或抛出自定义异常
                    }
                    Thread.sleep(50 + (long)Math.pow(2, retryCount)); // 简单的指数退避
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Restore interrupted status
            throw new RuntimeException("Cache rebuild interrupted", e);
        }
        // log.warn("Failed to rebuild cache for shop id {} after multiple retries.", id);
        return null; // 所有重试失败后返回
    }*/

    /*
    * 查询并解决缓存穿透
    * */
    /*public Shop queryWithPassThrough(Long id) {
        //从redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);

        //判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            //存在，返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }

        //判断是否命中为空值
        if (shopJson != null) {
            return null;
        }

        //不存在，根据id查询数据库
        Shop shop = getById(id);

        //数据库不存在，返回错误
        if (shop == null) {
            //将空值写入redis，解决缓存穿透
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            //返回错误信息
            return null;
        }

        //数据库存在，写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);

        return shop;
    }*/

    /*private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    *//*
    * 利用逻辑过期解决缓存击穿
    * *//*
    public Shop queryWithLogicalExpire(Long id) {
        //从redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);

        //判断是否存在
        if (StrUtil.isBlank(shopJson)) {
            //存在，返回
            return null;
        }

        //命中，把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();

        //判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            return shop;
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
                    this.saveShop2Redis(id, 20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    //释放锁
                    unlock(lockKey);
                }

            });
            
        }

        //失败，返回过期的商铺信息
        return shop;
    }*/


    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        //更新数据库
        updateById(shop);

        //删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}
