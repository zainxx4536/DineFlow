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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements IUserService {

    /**
     * 只匹配 openid 的目标唯一约束。
     * 兼容 uk_user_openid、user.uk_user_openid 等错误格式。
     */
    private static final Pattern OPENID_UNIQUE_CONSTRAINT = Pattern.compile(
            "for key ['`](?:[^'`]+\\.)?uk_user_openid['`]",
            Pattern.CASE_INSENSITIVE
    );

    private final WeChatProperties weChatProperties;
    private final ObjectMapper objectMapper;
    private final JwtProperties jwtProperties;

    /**
     * 用户登录。
     * 外层不加 @Transactional。
     * 首次注册只有一条 INSERT，由数据库自动提交。
     */
    @Override
    public UserLoginVO userLogin(UserLoginDTO dto) {

        if (dto == null || StrUtil.isBlank(dto.getCode())) {
            throw new LoginFailedException("临时登录凭证不能为空");
        }

        // openid 只能从后端验证过的微信响应中取得。
        String openid = getVerifiedOpenid(dto.getCode());

        // 已存在则使用原用户；不存在则尝试注册。
        User user = findOrCreateUser(openid);

        if (user.getId() == null || user.getId() <= 0) {
            throw new LoginFailedException("用户信息异常，登录失败");
        }

        // 到这里，用户已存在，或本次 INSERT 已成功提交。
        HashMap<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, user.getId());

        String token = JwtUtil.createJWT(
                jwtProperties.getUserSecretKey(),
                jwtProperties.getUserTtl(),
                claims
        );

        return UserLoginVO.builder()
                .id(user.getId())
                .openid(user.getOpenid())
                .token(token)
                .build();
    }

    /**
     * 调用微信接口并验证登录响应。
     * 不修改 openid 的大小写。
     */
    private String getVerifiedOpenid(String code) {

        HashMap<String, String> params = new HashMap<>();
        params.put("appid", weChatProperties.getAppid());
        params.put("secret", weChatProperties.getSecret());
        params.put("js_code", code);
        params.put("grant_type", "authorization_code");

        String responseJson;

        try {
            responseJson = HttpClientUtil.doGet(
                    "https://api.weixin.qq.com/sns/jscode2session",
                    params
            );
        } catch (RuntimeException exception) {
            throw new LoginFailedException("微信登录服务暂不可用，请稍后重试");
        }

        if (StrUtil.isBlank(responseJson)) {
            throw new LoginFailedException("微信登录失败，请重新登录");
        }

        WeChatLoginResponse response;

        try {
            response = objectMapper.readValue(
                    responseJson,
                    WeChatLoginResponse.class
            );
        } catch (JsonProcessingException exception) {
            throw new LoginFailedException("微信登录响应异常，请重新登录");
        }

        if (response == null) {
            throw new LoginFailedException("微信登录响应异常，请重新登录");
        }

        // 微信明确返回业务失败时，拒绝登录。
        if (response.getErrcode() != null && response.getErrcode() != 0) {
            throw new LoginFailedException("微信登录失败，请重新登录");
        }

        String openid = response.getOpenid();

        if (StrUtil.isBlank(openid) || openid.length() > 45) {
            throw new LoginFailedException("微信用户标识异常，登录失败");
        }

        return openid;
    }

    /**
     * 查询或创建用户。
     * 注册失败时直接抛出异常，不自动重试或查回。
     */
    private User findOrCreateUser(String openid) {

        User existing = findUserByOpenid(openid);

        if (existing != null) {
            return existing;
        }

        User newUser = User.builder()
                .openid(openid)
                .name("微信用户" + RandomUtil.randomNumbers(6))
                .build();

        try {
            // 单条 INSERT，由数据库自动提交。
            if (baseMapper.insert(newUser) != 1) {
                throw new LoginFailedException("用户注册失败，请重新登录");
            }

        } catch (DuplicateKeyException exception) {

            if (isOpenidUniqueConflict(exception)) {
                throw new LoginFailedException("登录发生并发冲突，请重新登录");
            }

            // 其他唯一冲突继续向外抛出。
            throw exception;
        }

        return newUser;
    }

    /**
     * 根据 openid 查询用户。
     */
    private User findUserByOpenid(String openid) {

        return lambdaQuery()
                .eq(User::getOpenid, openid)
                .one();
    }

    /**
     * 判断是否为 MySQL 1062，
     * 且冲突约束确实是 uk_user_openid。
     */
    private boolean isOpenidUniqueConflict(DuplicateKeyException exception) {

        for (Throwable cause = exception;
             cause != null;
             cause = cause.getCause()) {

            if (cause instanceof SQLException sqlException
                    && sqlException.getErrorCode() == 1062
                    && sqlException.getMessage() != null
                    && OPENID_UNIQUE_CONSTRAINT.matcher(sqlException.getMessage()).find()) {

                return true;
            }
        }

        return false;
    }
}