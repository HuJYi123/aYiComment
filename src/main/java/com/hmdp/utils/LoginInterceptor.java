package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

/**
 * className:LoginInterceptor
 * Package:com.hmdp.utils
 * Description:一步一脚印！
 *
 * @Date: 2023/4/22 21:15
 * @Author:2692243932@qq.com
 */
//@Component
public class LoginInterceptor implements HandlerInterceptor {

//    private StringRedisTemplate stringRedisTemplate;
//
//    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
//        this.stringRedisTemplate = stringRedisTemplate;
//    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

//        String token = request.getHeader("authorization");
//        if (StrUtil.isBlank(token)){
//            response.setStatus(401);
//            return false;
//        }
//        Map<Object,Object> userMap = stringRedisTemplate.opsForHash().entries(LOGIN_USER_KEY + token);
//        if (userMap.isEmpty()){
//            response.setStatus(401);
//            return false;
//        }
//
//        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
//        UserHolder.saveUser(userDTO);
//        //刷新用户有效期
//        stringRedisTemplate.expire(LOGIN_USER_KEY + token,LOGIN_USER_TTL,TimeUnit.MINUTES);
//        return true;
        UserDTO user = UserHolder.getUser();
        System.out.println("user是否为Null：" + (user == null));
        if (user == null){
            //不存在，拦截，返回401状态码
            response.setStatus(401);
            return false;
        }
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable ModelAndView modelAndView) throws Exception {
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
