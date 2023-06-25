package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * className:SimpleRedisLock
 * Package:com.hmdp.utils
 * Description:一步一脚印！
 *
 * @Date: 2023/6/12 15:06
 * @Author:2692243932@qq.com
 */
public class SimpleRedisLock implements ILock{

    private String lockName;
    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String lockName, StringRedisTemplate stringRedisTemplate) {
        this.lockName = lockName;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private static final String KEY_PREFIX = "lock:";
    //使用UUID作为前缀
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";

    /**
     * 使用静态代码块初始化Lua脚本对象
     */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }


    @Override
    public boolean tryLock(long timeOutSec) {
        //获取线程ID
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        //获取锁
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + lockName, threadId, timeOutSec, TimeUnit.SECONDS);
        //避免拆箱时出现null,导致异常
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unLock() {
        //调用Lua脚本
        stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(KEY_PREFIX + lockName),
                ID_PREFIX + Thread.currentThread().getId());
    }

//    @Override
//    public void unLock() {
//        //获取线程标示
//        String threadId = ID_PREFIX + Thread.currentThread().getId();
//        //获取锁中的标示
//        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + lockName);
//        if (threadId.equals(id)){
//            stringRedisTemplate.delete(KEY_PREFIX + lockName);
//        }
//    }
}
