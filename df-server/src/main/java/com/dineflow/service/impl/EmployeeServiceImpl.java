package com.dineflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.dineflow.constant.JwtClaimsConstant;
import com.dineflow.constant.MessageConstant;
import com.dineflow.constant.PasswordConstant;
import com.dineflow.constant.StatusConstant;
import com.dineflow.dto.EmployeeDTO;
import com.dineflow.dto.EmployeeLoginDTO;
import com.dineflow.dto.EmployeePageQueryDTO;
import com.dineflow.entity.Employee;
import com.dineflow.exception.AccountLockedException;
import com.dineflow.exception.AccountNotFoundException;
import com.dineflow.exception.PasswordErrorException;
import com.dineflow.mapper.EmployeeMapper;
import com.dineflow.properties.JwtProperties;
import com.dineflow.result.PageResult;
import com.dineflow.service.IEmployeeService;
import com.dineflow.utils.JwtUtil;
import com.dineflow.utils.ThreadLocalUtil;
import com.dineflow.vo.EmployeeLoginVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
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

    /**
     * 新增员工
     */
    @Override
    public void addEmployee(EmployeeDTO employeeDTO) {
        Employee employee = BeanUtil.copyProperties(employeeDTO, Employee.class);
        employee.setPassword(passwordEncoder.encode(PasswordConstant.DEFAULT_PASSWORD));

        employee.setStatus(StatusConstant.ENABLE);
        //createTime、updateTime、crateUser、updateUser 由 MyMetaObjectHandler 自动填充

        save(employee);
    }

    /**
     * 员工分页查询
     */
    @Override
    public PageResult<Employee> empPageQuery(EmployeePageQueryDTO employeePageQueryDTO) {
        //准备分页条件
        int pageNo = employeePageQueryDTO.getPage();
        int pageSize = employeePageQueryDTO.getPageSize();
        Page<Employee> page = Page.of(pageNo, pageSize);
        page.addOrder(new OrderItem().setColumn("update_time").setAsc(false));
        page.addOrder(new OrderItem().setColumn("id").setAsc(true));
        //请求中可能有姓名作为查询条件
        LambdaQueryWrapper<Employee> wrapper = new LambdaQueryWrapper<Employee>()
                .like(
                        StrUtil.isNotBlank(employeePageQueryDTO.getName()),
                        Employee::getName,
                        employeePageQueryDTO.getName()
                );
        //进行条件分页查询
        Page<Employee> p = page(page, wrapper);
        List<Employee> records = p.getRecords();
        for (Employee record : records) {
            record.setPassword("******");
        }
        //解析查询返回数据
        return new PageResult<>(p.getTotal(), records);
    }

    /**
     * 修改员工账号状态
     */
    @Override
    public void modifyEmpStatus(Long id, Integer status) {
        //注意：这种纯 Wrapper 更新，不会触发 Entity 自动填充
        //lambdaUpdate().set(Employee::getStatus, status).eq(Employee::getId, id).update();

        //要进行自动填充需要使用这种 Entity 更新
        Employee employee = Employee.builder()
                .id(id)
                .status(status)
                .build();

        updateById(employee);
    }

    /**
     * 根据 ID 查询员工信息（数据回显）
     */
    @Override
    public Employee getEmpInfoById(Long id) {
        Employee employee = getById(id);
        employee.setPassword("******");
        return employee;
    }

    /**
     * 修改员工信息
     */
    @Override
    public void editEmpInfo(EmployeeDTO employeeDTO) {
        Employee employee = BeanUtil.copyProperties(employeeDTO, Employee.class);
        updateById(employee);
    }
}
