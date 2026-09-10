package com.dineflow.controller.admin;


import com.dineflow.dto.EmployeeDTO;
import com.dineflow.dto.EmployeeLoginDTO;
import com.dineflow.dto.EmployeePageQueryDTO;
import com.dineflow.entity.Employee;
import com.dineflow.result.PageResult;
import com.dineflow.result.Result;
import com.dineflow.service.IEmployeeService;
import com.dineflow.vo.EmployeeLoginVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 员工信息 前端控制器
 * </p>
 *
 * @author zainxx
 * @since 2026-09-08
 */
@RestController
@RequestMapping("/admin/employee")
@Slf4j
@Api(tags = "员工相关接口")
@RequiredArgsConstructor
public class EmployeeController {

    private final IEmployeeService employeeService;

    /**
     * 员工登录
     */
    @PostMapping("/login")
    @ApiOperation("员工登录")
    public Result<EmployeeLoginVO> login(@RequestBody EmployeeLoginDTO employeeLoginDTO) {
        log.info("员工登录：{}", employeeLoginDTO);
        //将请求数据传入业务层进行处理
        EmployeeLoginVO employeeLoginVO = employeeService.login(employeeLoginDTO);
        return Result.success(employeeLoginVO);
    }

    /**
     * 新增员工
     */
    @PostMapping
    @ApiOperation("新增员工")
    public Result<String> addEmployee(@RequestBody EmployeeDTO employeeDTO) {
        log.info("新增员工：{}", employeeDTO);
        employeeService.addEmployee(employeeDTO);
        return Result.success();
    }

    /**
     * 员工分页查询
     */
    @GetMapping("/page")
    @ApiOperation("员工分页查询")
    public Result<PageResult<Employee>> empPageQuery(EmployeePageQueryDTO employeePageQueryDTO) {
        log.info("员工分页查询：{}", employeePageQueryDTO);
        PageResult<Employee> pageResult = employeeService.empPageQuery(employeePageQueryDTO);
        return Result.success(pageResult);
    }

    /**
     * 启用、禁用员工账号
     */
    @PostMapping("/status/{status}")
    @ApiOperation("修改员工账号状态")
    public Result<String> modifyEmpStatus(@PathVariable Integer status, @RequestParam("id") Long id) {
        log.info("修改员工账号状态：{},{}", id, status);
        employeeService.modifyEmpStatus(id, status);
        return Result.success();
    }

    /**
     * 根据 ID 查询员工信息（数据回显）
     */
    @GetMapping("/{id}")
    @ApiOperation("根据 ID 查询员工信息（数据回显）")
    public Result<Employee> getEmpInfoById(@PathVariable Long id) {
        log.info("根据 ID 查询员工信息（数据回显）：{}", id);
        Employee employee = employeeService.getEmpInfoById(id);
        return Result.success(employee);
    }


    /**
     * 修改员工信息
     */
    @PutMapping
    @ApiOperation("编辑员工信息")
    public Result<String> editEmpInfo(@RequestBody EmployeeDTO employeeDTO) {
        log.info("编辑员工信息：{}", employeeDTO);
        employeeService.editEmpInfo(employeeDTO);
        return Result.success();
    }
}
