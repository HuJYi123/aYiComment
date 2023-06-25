package com.hmdp;

import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * className:test1
 * Package:com.hmdp
 * Description:一步一脚印！
 *
 * @Date: 2023/6/12 0:32
 * @Author:2692243932@qq.com
 */
@SpringBootTest
public class test1 {

    @Autowired
    private RedisIdWorker redisIdWorker;

    @Test
    public void test(){
        long abcde = redisIdWorker.nextId("abcde");
        System.out.println(abcde);
    }
}
