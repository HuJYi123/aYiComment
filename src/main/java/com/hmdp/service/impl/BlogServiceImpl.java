package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {


    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IFollowService iFollowService;

    @Resource
    private IUserService userService;
    @Override
    public Result queryBlogById(Long id) {
        Blog blog = getById(id);
        if (blog == null){
            return Result.fail("笔记不存在");
        }
        setUser(blog);
        //查看Blog是否被点赞
        isBlogLike(blog);
        return Result.ok(blog);
    }

    private void isBlogLike(Blog blog) {
        UserDTO user = UserHolder.getUser();
        if (user == null){
            //为null,则不需要查询
            return;
        }
        //获取登录用户
        Long userId = user.getId();
        //判断当前用户是否已经点赞
        Double score = stringRedisTemplate.opsForZSet().score(BLOG_LIKED_KEY + blog.getId(), userId.toString());
        blog.setIsLike(score != null);

    }

    @Override
    public Result queryHotBlog(Integer current) {
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog ->{
            this.setUser(blog);
            this.isBlogLike(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result likeBlog(Long id) {
        //获取登录用户
        Long userId = UserHolder.getUser().getId();
        //判断当前用户是否已经点赞,查SortSet里面的Score值
        Double score = stringRedisTemplate.opsForZSet().score(BLOG_LIKED_KEY + id, userId.toString());
        if (score == null){
            //如果未点赞，可以点赞
            //数据库点赞数+1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            if (isSuccess) {
//                blog.setIsLike(BooleanUtil.isTrue(isMember));
                //保存用户到Redis的Set集合
                //使用SortSet保存，利用它的score值，获取前五名
                stringRedisTemplate.opsForZSet().add(BLOG_LIKED_KEY + id,userId.toString(),System.currentTimeMillis());
            }
        }else {
            //如果已点赞，取消点赞
            //数据库点赞数-1
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            //把用户从Redis的Set集合移除
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().remove(BLOG_LIKED_KEY + id,userId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        String key = BLOG_LIKED_KEY + id;
        //查询前5的点赞用户
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5 == null || top5.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        //将列表ids转换为字符串，并加上,
        String idStr = StrUtil.join(",", ids);
        //根据用户ID查询用户WHERE id in (5,1) order by field(id,5,1);
        /**
         * 因为使用 in 不会按照我们传进去的id顺序进行查询，所以使用 order by 进行手动排列
         */
        List<UserDTO> userDTOS = userService.query()
                .in("id",ids).last("ORDER BY FIELD(id," + idStr  + ")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOS);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店笔记
        boolean isSuccess = save(blog);
        if (!isSuccess){
            return Result.fail("新增笔记失败");
        }
        //查询笔记作者的所有粉丝 select * from tb_follow where follow_user_id = ?
        List<Follow> follows = iFollowService.query().eq("follow_user_id", user.getId()).list();
        //推送笔记ID给所有粉丝
        for (Follow follow : follows){
            //获取粉丝id
            Long userId = follow.getUserId();
            //推送笔记id到粉丝收件箱，使用时间戳作为score值
            String key = "feed:" +userId;
            stringRedisTemplate.opsForZSet().add(key,blog.getId().toString(),System.currentTimeMillis());
        }
        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        //获取当前用户
        Long userId = UserHolder.getUser().getId();
        //查询收件箱  ZREVRANGEBYSCORE(大->小) KEY MAX MIN OFFSET COUNT
        String key = FEED_KEY + userId;
        //返回值里有笔记id和当前score值（时间戳）
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        //非空判断
        if (typedTuples == null || typedTuples.isEmpty()){
            return Result.ok();
        }
        //解析数据: bolgId、minTime(时间戳） offset
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;
        int os = 1;  //偏移量
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            //获取id
            ids.add(Long.valueOf(typedTuple.getValue()));
            //获取分数（时间戳）
            long time = typedTuple.getScore().longValue();
            if (time == minTime){
                os++;
            }else {
                minTime = time;
                os = 1;
            }
        }
        String idStr = StrUtil.join(",",ids);
        List<Blog> blogs = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for (Blog blog : blogs) {
            setUser(blog);
            isBlogLike(blog);
        }
        //封装并返回
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setOffset(os);
        scrollResult.setMinTime(minTime);

        return Result.ok(scrollResult);
    }

    /**
     * 查询设置笔记相关博客信息
     * @param blog
     */
    private void setUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
