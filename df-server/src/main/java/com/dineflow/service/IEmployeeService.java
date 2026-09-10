package com.dineflow.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.dineflow.dto.EmployeeDTO;
import com.dineflow.dto.EmployeeLoginDTO;
import com.dineflow.dto.EmployeePageQueryDTO;
import com.dineflow.entity.Employee;
import com.dineflow.result.PageResult;
import com.dineflow.vo.EmployeeLoginVO;

/**
 * <p>
 * 员工信息 服务类
 * </p>
 *
 * @author zainxx
 * @since 2026-09-08
 */
public interface IEmployeeService extends IService<Employee> {

    EmployeeLoginVO login(EmployeeLoginDTO employeeLoginDTO);

    void addEmployee(EmployeeDTO employeeDTO);

    PageResult<Employee> empPageQuery(EmployeePageQueryDTO employeePageQueryDTO);
}
