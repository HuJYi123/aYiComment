package com.hmdp.utils;

/**
 * className:ILock
 * Package:com.hmdp.utils
 * Description:一步一脚印！
 *
 * @Date: 2023/6/12 15:03
 * @Author:2692243932@qq.com
 */
public interface ILock {

    /**
     * 非阻塞获取锁，尝试获取
     * @param timeOutSec
     * @return
     */
    boolean tryLock(long timeOutSec);

    void unLock();
}
