package com.dineflow.interceptor;


import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpStatus;
import com.dineflow.constant.JwtClaimsConstant;
import com.dineflow.properties.JwtProperties;
import com.dineflow.utils.JwtUtil;
import com.dineflow.utils.ThreadLocalUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 用户端请求拦截器，用来校验登录状态
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class UserRequestInterceptor implements HandlerInterceptor {

    private final JwtProperties jwtProperties;

    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler) {

        // 非 Controller 请求直接放行
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        // 获取用户 Token
        String token = request.getHeader(
                jwtProperties.getUserTokenName()
        );

        if (StrUtil.isBlank(token)) {
            log.info("用户令牌为空");
            response.setStatus(HttpStatus.HTTP_UNAUTHORIZED);
            return false;
        }

        try {
            // 注意：这里必须使用用户端密钥
            Claims claims = JwtUtil.parseJWT(
                    jwtProperties.getUserSecretKey(),
                    token
            );

            Long userId = Long.valueOf(
                    claims.get(JwtClaimsConstant.USER_ID).toString()
            );

            ThreadLocalUtil.setCurrentId(userId);

            log.info("用户令牌校验成功，当前用户ID：{}", userId);

            return true;

        } catch (Exception e) {
            log.warn("用户令牌解析失败");
            response.setStatus(HttpStatus.HTTP_UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterCompletion(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler,
            @Nullable Exception ex) {

        ThreadLocalUtil.removeCurrentId();
    }
}
