package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService iSeckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;
    private IVoucherOrderService proxy;

    //定义阻塞队列
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandle());
    }

    private class VoucherOrderHandle implements Runnable {
        String queueName = "stream.orders";

        @Override
        public void run() {
            while (true) {
                try {
                    //获取消息队列中的订单信息
                    List<MapRecord<String,Object,Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1","c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    //判断消息是否获取成功
                    if (list == null || list.isEmpty()){
                    //获取失败，说明没用信息，继续下一次循环
                        continue;
                    }
                    //解析消息中的订单信息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    //获取成功，创建订单
                    handleVoucherOrder(voucherOrder);
                    //ACK确认
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());
                } catch (Exception e) {
                    log.error("处理订单异常" + e);
                    handlePendingList();
                }
            }
        }

        private void handlePendingList() {
            while (true) {
                try {
                    //获取pending-list中的订单信息
                    List<MapRecord<String,Object,Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1","c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );
                    //判断消息是否获取成功
                    if (list == null || list.isEmpty()){
                        //获取失败，说明没用信息，继续下一次循环
                        break;
                    }
                    //解析消息中的订单信息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    //获取成功，创建订单
                    handleVoucherOrder(voucherOrder);
                    //ACK确认
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());
                } catch (Exception e) {
                    log.error("处理pending-list异常" + e);
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }
    }

//    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
//    private class VoucherOrderHandle implements Runnable {
//
//        @Override
//        public void run() {
//            while (true) {
//                try {
//                    //获取队列中的订单信息
//                    VoucherOrder voucherOrder = orderTasks.take();
//                    //创建订单
//                    handleVoucherOrder(voucherOrder);
//                } catch (Exception e) {
//                    log.error("处理订单异常" + e);
//                }
//            }
//        }
//    }
    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        //获取锁
        boolean isLock = lock.tryLock();
        //是否获取成功
        if (!isLock) {
            //获取失败
            log.error("不允许重复下单");
            return;
        }
        try {
            //获取代理对象（事务），使用代理加载，执行事务，提高线程的安全。
            proxy.createVoucherOrder(voucherOrder);
//        }
        } finally {
            lock.lock();
        }

    }
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        //一人一单
        long userId = voucherOrder.getUserId();
        //查询订单
        int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        //判断是否存在
        if (count > 0) {
            //用户已经购买过
           log.error("用户已经下单过一次！");
           return;
        }

        //扣减库存
        boolean success = iSeckillVoucherService.update().setSql("stock = stock - 1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock", 0)//使用乐观锁解决超卖问题
                .update();
        if (!success) {
            //扣减失败
            log.error("库存不足！");
            return;
        }
        save(voucherOrder);
    }
    /**
     * 使用静态代码块初始化Lua脚本对象
     */
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");
        //执行lua脚本
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT, Collections.emptyList(),
                voucherId.toString(), userId.toString(),String.valueOf(orderId));
        //判断结果是否为0
        int r = result.intValue();
        //不为0，代表没用购买资格
        if (r != 0) {
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        //获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        //返回订单id
        return Result.ok(orderId);
    }




//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        Long userId = UserHolder.getUser().getId();
//        //执行lua脚本
//        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT, Collections.emptyList(),
//                voucherId.toString(), userId.toString());
//        //判断结果是否为0
//        int r = result.intValue();
//        //不为0，代表没用购买资格
//        if (r != 0) {
//            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
//        }
//        //为0，有购买资格，把下单信息保存到阻塞队列
//        VoucherOrder voucherOrder = new VoucherOrder();
//        //订单id
//        long orderId = redisIdWorker.nextId("order");
//        voucherOrder.setId(orderId);
//        //用户id
//        voucherOrder.setUserId(userId);
//        //代金卷id
//        voucherOrder.setVoucherId(voucherId);
//        //放入阻塞队列
//        orderTasks.add(voucherOrder);
//        //获取代理对象
//        proxy = (IVoucherOrderService) AopContext.currentProxy();
//
//        //返回订单id
//        return Result.ok(orderId);
//    }


//    @Override
//    public Result seckillVoucher(Long voucherId) {
//        //查询优惠券
//        SeckillVoucher voucher = iSeckillVoucherService.getById(voucherId);
//        //判断秒杀是否开始
//        if (voucher.getBeginTime().isAfter(LocalDateTime.now())){
//            //未开始
//            return Result.fail("秒杀尚未开始！");
//        }
//        //判断秒杀是否结束
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())){
//            //已结束
//            return Result.fail("秒杀已过期！");
//        }
//        //判断库存是否充足
//        if (voucher.getStock() < 1){
//            //库存不足
//            return Result.fail("库存不足！");
//        }
//        Long userId = UserHolder.getUser().getId();
//
//        /**
//         * 为了实现只对每个用户加锁，并且还考虑到释放锁时事务也要执行到，因此把锁加在此处
//         * 以用户ID为锁对象，实现只对每个用户加锁。
//         */
////        synchronized (userId.toString().intern()){
//        //使用分布式锁
//        //创建锁对象
////        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
    //使用Redisson获取锁
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        //获取锁
//        boolean isLock = lock.tryLock();
//        //是否获取成功
//        if( ! isLock){
//            //获取失败
//            return Result.fail("不允许重复下单");
//        }
//        try {
//        //获取代理对象（事务），使用代理加载，执行事务，提高线程的安全。
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
////        }
//        }finally {
//            lock.lock();
//        }
//    }

    /**
     * 考虑到加锁只需对同个用户进行线程安全，因此不在方法上直接加synchronized
     *
     * @param voucherId
     * @return
     */
//    @Transactional
//    public Result createVoucherOrder(Long voucherId) {
//        //一人一单
//        long userId = UserHolder.getUser().getId();
//        //查询订单
//        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
//        //判断是否存在
//        if (count > 0) {
//            //用户已经购买过
//            return Result.fail("用户已经下单过一次！");
//        }
//
//        //扣减库存
//        boolean success = iSeckillVoucherService.update().setSql("stock = stock - 1")
//                .eq("voucher_id", voucherId)
//                .gt("stock", 0)//使用乐观锁解决超卖问题
//                .update();
//        if (!success) {
//            //扣减失败
//            return Result.fail("库存不足！");
//        }
//
//
//        //创建订单
//        VoucherOrder voucherOrder = new VoucherOrder();
//        //订单ID
//        long orderID = redisIdWorker.nextId("order");
//        voucherOrder.setId(orderID);
//        //用户ID
//
//        voucherOrder.setUserId(userId);
//        //代金券ID
//        voucherOrder.setVoucherId(voucherId);
//        //增加订单
//        save(voucherOrder);
//        return Result.ok(orderID);
//    }
}
