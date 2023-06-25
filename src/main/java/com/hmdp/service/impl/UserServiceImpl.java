package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author
 * @since 2021-12-22
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result sendPhone(String phone, HttpSession session) {
        //校验手机号码格式是否正确
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机格式错误");
        }
        //使用工具生成6位随机验证码
        String s = RandomUtil.randomNumbers(6);
//        session.setAttribute("code",s);  使用redis代替session
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,s,LOGIN_CODE_TTL, TimeUnit.MINUTES);
        log.debug("短信验证码发送成功，验证码："+s);
        return Result.ok();
    }
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        //校验手机号码格式是否正确
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机格式错误");
        }
//        Object code = session.getAttribute("code");
        String code = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        //判断输入验证码是否正确
        if(code == null || !code.equals(loginForm.getCode())){
            return Result.fail("验证码错误");
        }
        //查询用户
        User user = query().eq("phone", phone).one();
        if(user==null){
            //不存在，创建新用户
            user = createUser(phone);
        }
        //通过UUID工具获取随机token,为了提高安全性，使用token用于登录令牌
        String token = UUID.randomUUID().toString(true);
        //使用工具将转换对象
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
//        UserHolder.saveUser(userDTO);

        //使用工具将UserDTO对象转换为map对象
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName,fielValue)->fielValue.toString()));
        //存储到redis中
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY+token,userMap);
        //设置有效期
        stringRedisTemplate.expire(LOGIN_USER_KEY+token,LOGIN_USER_TTL,TimeUnit.MINUTES);
//        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
        return Result.ok(token);
    }

    @Override
    public Result sign() {
        Long userId = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        //获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        stringRedisTemplate.opsForValue().setBit(key,dayOfMonth - 1,true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        Long userId = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        //获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        //获取本月截止今天为止的所有的签到记录，返回的是一个十进制的数字
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );
        if (result == null || result.isEmpty()){
            //没有任何签到结果
            return Result.ok(0);
        }
        Long num = result.get(0);
        if (num == null || num == 0){
            return Result.ok(0);
        }
        //循环遍历
        int count = 0;
        while (true) {
            //让这个数字与1做运算，得到数字的最后一个bit位//判断这个bit位是否为0
            if ((num & 1) == 0){
            //为0，说明未签到，结束
                break;
            }else {
                //不为0，说明已签到，计数器+1
                count++;
            }
            //把数字右移一位，抛弃最后一个bit位，继续下一个bit位
            num >>>= 1;
        }
        return Result.ok(count);
    }

    private User createUser(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomString(10));
        boolean save = save(user);
        return user;
    }
}
