package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryByList() {
        String key = CACHE_SHOP_TYPE_KEY;
        Set<String> members = stringRedisTemplate.opsForSet().members(key);
        Iterator<String> iterator = members.iterator();
        List<ShopType> list = new ArrayList<>();
        while (iterator.hasNext()){
            String next = iterator.next();
            list.add(JSONUtil.toBean(next,ShopType.class));
        }
        if(!list.isEmpty()){
            return Result.ok(list);
        }
        List<ShopType> sort = query().orderByAsc("sort").list();
        if(sort == null){
            return Result.fail("店铺类型不存在");
        }
        SetOperations<String, String> sss = stringRedisTemplate.opsForSet();
        for (ShopType st : sort) {
            sss.add(CACHE_SHOP_TYPE_KEY,JSONUtil.toJsonStr(st));
        }
        return Result.ok(sort);
    }
}
