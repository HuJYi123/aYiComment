-- 优惠券id
local vouvherId = ARGV[1]

--用户id
local userId = ARGV[2]
--订单id
local orderId = ARGV[3]

--数据key
--库存key
local stockKey = 'seckill:stock:' .. vouvherId   --合并字符串

--订单id
local orderKey = 'seckill:order:' .. vouvherId

--脚本业务
--判断库存是否充足 get stockKey
if (tonumber(redis.call('get',stockKey)) <= 0) then
    --库存不足
    return 1
end
--判断用户是否下单 SISMEMBER orderKey userId
if (redis.call('sismember',orderKey,userId) == 1) then
    --存在，说明是重复下单
    return 2
end
--扣库存
redis.call('incrby',stockKey,-1)
--下单
redis.call('sadd',orderKey,userId)
--可以下单，发送消息到队列中
redis.call('xadd','stream.orders','*','userId',userId,'voucherId',vouvherId,'id',orderId)
return 0