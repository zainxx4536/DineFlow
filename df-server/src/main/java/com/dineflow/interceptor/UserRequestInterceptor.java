package com.dineflow.interceptor;

import cn.hutool.core.util.StrUtil;
import com.dineflow.constant.JwtClaimsConstant;
import com.dineflow.properties.JwtProperties;
import com.dineflow.result.Result;
import com.dineflow.utils.JwtUtil;
import com.dineflow.utils.ThreadLocalUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
@Slf4j
@RequiredArgsConstructor
public class UserRequestInterceptor implements HandlerInterceptor {

    private final JwtProperties jwtProperties;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler) throws IOException {

        // 先清理当前线程可能残留的身份。
        ThreadLocalUtil.removeCurrentId();

        // 沿用当前对非 Controller 请求的处理。
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        String token = request.getHeader(jwtProperties.getUserTokenName());

        if (StrUtil.isBlank(token)) {
            return unauthorized(response);
        }

        try {
            Claims claims = JwtUtil.parseJWT(jwtProperties.getUserSecretKey(), token);

            Object value = claims.get(JwtClaimsConstant.USER_ID);

            if (value == null) {
                return unauthorized(response);
            }

            Long currentId = Long.valueOf(value.toString());

            if (currentId <= 0) {
                return unauthorized(response);
            }

            // 所有认证检查通过后，才设置本次身份。
            ThreadLocalUtil.setCurrentId(currentId);

            log.debug("身份校验通过，id={}", currentId);

            return true;

        } catch (JwtException | IllegalArgumentException exception) {
            // 不记录 Token 或异常原文。
            return unauthorized(response);
        }
    }

    private boolean unauthorized(HttpServletResponse response) throws IOException {

        ThreadLocalUtil.removeCurrentId();

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");

        objectMapper.writeValue(
                response.getWriter(),
                Result.error("用户未登录或登录已失效，请重新登录")
        );

        return false;
    }

    @Override
    public void afterCompletion(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler,
            @Nullable Exception exception) {

        ThreadLocalUtil.removeCurrentId();
    }
}