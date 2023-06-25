package com.hmdp.utils;

import lombok.Data;

import java.time.LocalDateTime;


/**
 * 用来设置逻辑过期时间和设置存储数据
 */
@Data
public class RedisData {
    private LocalDateTime expireTime;  //存储过期时间
    private Object data;  //存储对象数据，例如Shop对象
}
