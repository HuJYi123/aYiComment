package com.hmdp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;

/**
 * className:TessHyperLogLog
 * Package:com.hmdp
 * Description:一步一脚印！
 *
 * @Date: 2023/6/25 18:48
 * @Author:2692243932@qq.com
 */
@SpringBootTest
public class TessHyperLogLog {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Test
    public void test(){
        String[] values = new String[1000];
        int j = 0;
        for (int i = 0; i < 1000000; i++) {
            j = i % 1000;
            values[j] = "user_" + i;
            if (j == 999){
                stringRedisTemplate.opsForHyperLogLog().add("hl1",values);
            }
        }
        Long count = stringRedisTemplate.opsForHyperLogLog().size("hl1");
        System.out.println("count:" + count);
    }
}
