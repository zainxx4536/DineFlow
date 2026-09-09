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
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 请求拦截器，用来校验登录状态
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AdminRequestInterceptor implements HandlerInterceptor {

    private final JwtProperties jwtProperties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //获取请求头中的 Token，并校验 Token 的正确性
        String token = request.getHeader("token");
        if (StrUtil.isBlank(token)) {
            //token 为空，返回错误码，由前端处理跳转到登录页面，拦截该请求
            log.info("令牌为空, 返回错误结果");
            response.setStatus(HttpStatus.HTTP_UNAUTHORIZED);
            return false;
        }
        //尝试解析 Token，若抛出异常，则拦截请求
        try {
            Claims claims = JwtUtil.parseJWT(jwtProperties.getAdminSecretKey(), token);
            Long empId = Long.valueOf(claims.get(JwtClaimsConstant.EMP_ID).toString());
            ThreadLocalUtil.setCurrentId(empId);
            log.info("令牌校验成功，当前员工ID：{}", empId);
            return true;
        } catch (Exception e) {
            log.warn("解析令牌失败, 返回错误结果");
            response.setStatus(HttpStatus.HTTP_UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) throws Exception {
        //清空 ThreadLocal
        ThreadLocalUtil.removeCurrentId();
    }
}
