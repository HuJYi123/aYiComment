package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * className:RedisIdWorker
 * Package:com.hmdp.utils
 * Description: 全局ID生成器
 *
 * @Date: 2023/6/11 21:19
 * @Author:2692243932@qq.com
 */
@Component
public class RedisIdWorker {

    private StringRedisTemplate stringRedisTemplate;

    /**
     *定义开始时间，从2022年开始
     * 将当前时间转换为从1970年1月1日0时0分0秒开始计算的秒数（即Unix时间戳）
     *  LocalDateTime time = LocalDateTime.of(2022,1,1,0,0,0);
     *         long l = time.toEpochSecond(ZoneOffset.UTC);
     */
    private static final long BEGIN_TIMESTAMP = 1640995200L;
    private static final int COUNT_BITS = 32;//序列号位数

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long nextId(String keyPrefix){
        //生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;
        System.out.println(timestamp);
        //生成序列号
        //获取当前日期，精确到天,用于增加key值的复杂度
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        System.out.println(date);
        //自增长
        long count = stringRedisTemplate.opsForValue().increment("icr" + keyPrefix + ":" + date);
        System.out.println(count);
        //拼接并返回
        return timestamp << COUNT_BITS | count;
    }



}
