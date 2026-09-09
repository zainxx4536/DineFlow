package com.dineflow.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.dineflow.constant.JwtClaimsConstant;
import com.dineflow.constant.MessageConstant;
import com.dineflow.constant.StatusConstant;
import com.dineflow.dto.EmployeeLoginDTO;
import com.dineflow.entity.Employee;
import com.dineflow.exception.AccountLockedException;
import com.dineflow.exception.AccountNotFoundException;
import com.dineflow.exception.PasswordErrorException;
import com.dineflow.mapper.EmployeeMapper;
import com.dineflow.properties.JwtProperties;
import com.dineflow.service.IEmployeeService;
import com.dineflow.utils.JwtUtil;
import com.dineflow.vo.EmployeeLoginVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Objects;

/**
 * <p>
 * 员工信息 服务实现类
 * </p>
 *
 * @author zainxx
 * @since 2026-09-08
 */
@Service
@RequiredArgsConstructor
public class EmployeeServiceImpl extends ServiceImpl<EmployeeMapper, Employee> implements IEmployeeService {

    private final PasswordEncoder passwordEncoder;

    private final JwtProperties jwtProperties;

    /**
     * 员工登录
     */
    @Override
    public EmployeeLoginVO login(EmployeeLoginDTO employeeLoginDTO) {
        //根据用户名查找用户
        Employee employee = lambdaQuery()
                .eq(Employee::getUsername, employeeLoginDTO.getUsername())
                .one();

        //处理异常情况
        if (employee == null) {
            //账号不存在
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }
        if (!passwordEncoder.matches(employeeLoginDTO.getPassword(), employee.getPassword())) {
            //密码错误
            throw new PasswordErrorException(MessageConstant.PASSWORD_ERROR);
        }
        if (Objects.equals(employee.getStatus(), StatusConstant.DISABLE)) {
            //账号被锁定
            throw new AccountLockedException(MessageConstant.ACCOUNT_LOCKED);
        }

        //登录成功，生成 Token，封装数据并返回
        HashMap<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.EMP_ID, employee.getId());
        String token = JwtUtil.createJWT(
                jwtProperties.getAdminSecretKey(),
                jwtProperties.getAdminTtl(),
                claims
        );
        return EmployeeLoginVO.builder()
                .id(employee.getId())
                .name(employee.getName())
                .token(token)
                .userName(employee.getUsername())
                .build();
    }
}
