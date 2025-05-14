package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    /*
    * 查询商铺类型列表
    * */
    public Result queryList() {
        //redis查询
        String shopTypeJson = stringRedisTemplate.opsForValue().get("cache:shopType:");
        if (StrUtil.isNotBlank(shopTypeJson)) {
            List<ShopType> shopTypeList = JSONUtil.toList(shopTypeJson, ShopType.class);
            return Result.ok(shopTypeList);
        }

        //sql查询
        List<ShopType> shopTypeList = baseMapper.selectList(null);
        if (shopTypeList == null && shopTypeList.size() == 0) {
            return Result.fail("没有店铺类型");
        }

        //redis缓存
        stringRedisTemplate.opsForValue().set("cache:shopType:", JSONUtil.toJsonStr(shopTypeList));
        stringRedisTemplate.expire("cache:shopType:", CACHE_SHOP_TTL, TimeUnit.MINUTES);

        return Result.ok(shopTypeList);
    }
}
