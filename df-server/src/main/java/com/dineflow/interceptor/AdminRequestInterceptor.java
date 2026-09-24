package com.dineflow.interceptor;


import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpStatus;
import com.dineflow.constant.JwtClaimsConstant;
import com.dineflow.constant.MessageConstant;
import com.dineflow.properties.JwtProperties;
import com.dineflow.utils.JwtUtil;
import com.dineflow.utils.ThreadLocalUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 管理端请求拦截器，用来校验登录状态
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AdminRequestInterceptor implements HandlerInterceptor {

    private final JwtProperties jwtProperties;

    @Override
    public boolean preHandle(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {

        // 获取请求头中的 Token
        String token = request.getHeader(
                jwtProperties.getAdminTokenName()
        );

        if (StrUtil.isBlank(token)) {
            log.info(MessageConstant.EMPLOYEE_NOT_LOGIN);
            response.setStatus(HttpStatus.HTTP_UNAUTHORIZED);
            return false;
        }

        try {
            // 使用管理端密钥解析 Token
            Claims claims = JwtUtil.parseJWT(
                    jwtProperties.getAdminSecretKey(),
                    token
            );

            Long empId = Long.valueOf(
                    claims.get(JwtClaimsConstant.EMP_ID).toString()
            );

            ThreadLocalUtil.setCurrentId(empId);

            log.info("管理员令牌校验成功，当前员工ID：{}", empId);

            return true;

        } catch (Exception e) {
            log.warn("管理员令牌解析失败");
            response.setStatus(HttpStatus.HTTP_UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler, @Nullable Exception ex) {

        ThreadLocalUtil.removeCurrentId();
    }
}
