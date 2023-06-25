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
import java.util.function.Consumer;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

/**
 * className:CacheClient
 * Package:com.hmdp.utils
 * Description:  缓存工具类
 *
 * @Date: 2023/4/26 19:57
 * @Author:2692243932@qq.com
 */

@Slf4j
@Component
public class CacheClient {
    private final StringRedisTemplate stringRedisTemplate;

    //使用构造方法注入 StringRedisTemplate
    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    //写入Redis中并设置过期时间
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    //写入Redis中并设置逻辑过期时间
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    //利用缓存空值解决缓存穿透
    public <R,ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallback,
            Long time , TimeUnit unit){
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        //redis中存在,shopJson有值才为true
        if (StrUtil.isNotBlank(json)){
            return JSONUtil.toBean(json, type);
        }
        //shopJson为null 或者为""为false
        if(json != null){ //进入则表示shopJson为""
//            return Result.fail("店铺信息不存在");
            return null;
        }
        //redis中不存在,查询数据库
        R r = dbFallback.apply(id);
        if (r == null){
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
//            return Result.fail("店铺不存在");
            return null;
        }
        //写入缓存并设置过期时间
        this.set(key,r,time,unit);
        return r;
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    //逻辑过期解决缓存击穿
    public <R,ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallback,Long time , TimeUnit unit){
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        //为空直接返回，因为逻辑过期解决击穿问题，会提前将高并发Key存入Redis中
        if (StrUtil.isBlank(json)){
            return null;
        }
        //命中,需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        //判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            //未过期,直接返回店铺信息
            return r;
        }
        //已过期,需要缓存重建
        //缓存重建
        //获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        //判断是否获取成功
        if(isLock){
            //成功,开启独立线程,实现缓存重建（实际上是更新逻辑过期时间）
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    R r1 = dbFallback.apply(id);
                    this.setWithLogicalExpire(key,r1,time,unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    unLock(lockKey);
                }
            });
        }

        //返回过期的商铺信息
        return r;
    }

    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        //直接返回flag可能会出现错误，需要使用一个工具类进行转换
        return BooleanUtil.isTrue(flag);
    }
    //释放锁
    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }

}
