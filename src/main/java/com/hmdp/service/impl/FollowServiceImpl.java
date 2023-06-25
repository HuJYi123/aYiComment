package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService userService;

    /**
     *
     * @param followUserId 被关注用户的id
     * @param isFollow 是关注还是取关
     * @return
     */
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        //获取登录用户id
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" + userId;
        //判断是关注还是取关
        if (isFollow){
            //关注，新增数据
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = save(follow);   //往数据库增加数据
            if (isSuccess){
                stringRedisTemplate.opsForSet().add(key,followUserId.toString()); //放入set集合中，以便后续完成共同关注功能
            }
        }else {
            //取关，删除数据
            boolean isSuccess = remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", followUserId));
            if (isSuccess){
                stringRedisTemplate.opsForSet().remove(key,followUserId.toString());//放入set集合中，以便后续完成共同关注功能
            }
        }

        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        //获取登录用户id
        Long userId = UserHolder.getUser().getId();
        Integer count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        return Result.ok(count > 0);
    }

    /**
     *
     * @param id 为当前点进的用户
     * @return
     */
    @Override
    public Result followCommons(Long id) {
        //获取当前用户id
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" + userId;
        //求交集
        String key2 = "follows:" + id;
        System.out.println("key1:" + key);
        System.out.println("key2:" + key2);
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key, key2);
        if (intersect == null || intersect.isEmpty()){
            System.err.println("空的");
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> users = userService.listByIds(ids).stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(users);
    }
}
