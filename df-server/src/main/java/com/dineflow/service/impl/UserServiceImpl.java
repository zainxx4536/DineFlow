package com.dineflow.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.dineflow.constant.JwtClaimsConstant;
import com.dineflow.dto.UserLoginDTO;
import com.dineflow.entity.User;
import com.dineflow.entity.WeChatLoginResponse;
import com.dineflow.exception.LoginFailedException;
import com.dineflow.mapper.UserMapper;
import com.dineflow.properties.JwtProperties;
import com.dineflow.properties.WeChatProperties;
import com.dineflow.service.IUserService;
import com.dineflow.utils.HttpClientUtil;
import com.dineflow.utils.JwtUtil;
import com.dineflow.vo.UserLoginVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;

/**
 * <p>
 * 用户信息 服务实现类
 * </p>
 *
 * @author zainxx
 * @since 2026-09-08
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    private final WeChatProperties weChatProperties;

    private final ObjectMapper objectMapper;

    private final JwtProperties jwtProperties;

    /**
     * 微信小程序用户登录
     */
    @Override
    public UserLoginVO userLogin(UserLoginDTO userLoginDTO) {

        String code = userLoginDTO.getCode();

        if (StrUtil.isBlank(code)) {
            throw new LoginFailedException("临时登录凭证code错误，登录失败！");
        }

        // 构建微信登录请求参数
        HashMap<String, String> paramMap = new HashMap<>();
        paramMap.put("appid", weChatProperties.getAppid());
        paramMap.put("secret", weChatProperties.getSecret());
        paramMap.put("js_code", code);
        paramMap.put("grant_type", "authorization_code");

        // 请求微信接口
        String responseJson = HttpClientUtil.doGet(
                "https://api.weixin.qq.com/sns/jscode2session",
                paramMap
        );

        if (StrUtil.isBlank(responseJson)) {
            throw new LoginFailedException("用户登录失败！");
        }

        // 解析微信响应
        WeChatLoginResponse response;

        try {
            response = objectMapper.readValue(
                    responseJson,
                    WeChatLoginResponse.class
            );
        } catch (JsonProcessingException e) {
            throw new LoginFailedException("微信登录响应解析失败！");
        }

        // 微信返回业务错误
        if (response.getErrcode() != null && response.getErrcode() != 0) {
            throw new LoginFailedException("微信登录失败：" + response.getErrmsg());
        }

        String openid = response.getOpenid();

        if (StrUtil.isBlank(openid)) {
            throw new LoginFailedException("获取用户 openid 失败！");
        }

        // 根据 openid 查询用户
        User user = lambdaQuery()
                .eq(User::getOpenid, openid)
                .one();

        // 新用户自动注册
        if (user == null) {
            user = User.builder()
                    .openid(openid)
                    .name("微信用户" + RandomUtil.randomNumbers(6))
                    .build();

            save(user);
        }

        // 构建 JWT 载荷
        HashMap<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, user.getId());

        // 生成 token
        String token = JwtUtil.createJWT(
                jwtProperties.getUserSecretKey(),
                jwtProperties.getUserTtl(),
                claims
        );

        return UserLoginVO.builder()
                .id(user.getId())
                .openid(openid)
                .token(token)
                .build();
    }
}
