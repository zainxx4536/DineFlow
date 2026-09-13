package com.dineflow.controller.admin;


import com.dineflow.dto.SetmealDTO;
import com.dineflow.dto.SetmealPageQueryDTO;
import com.dineflow.entity.Setmeal;
import com.dineflow.result.PageResult;
import com.dineflow.result.Result;
import com.dineflow.service.ISetmealService;
import com.dineflow.vo.SetmealVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 套餐 前端控制器
 * </p>
 *
 * @author zainxx
 * @since 2026-09-08
 */
@RestController
@RequestMapping("/admin/setmeal")
@Slf4j
@Api(tags = "套餐相关接口")
@RequiredArgsConstructor
public class SetmealController {

    private final ISetmealService setmealService;

    /**
     * 新增套餐
     */
    @PostMapping
    @ApiOperation("新增套餐")
    public Result<String> addSetmeal(@RequestBody SetmealDTO setmealDTO) {
        log.info("新增套餐：{}", setmealDTO);
        setmealService.addSetmeal(setmealDTO);
        return Result.success();
    }

    /**
     * 套餐分页查询
     */
    @GetMapping("/page")
    @ApiOperation("套餐分页查询")
    public Result<PageResult<SetmealVO>> setmealPageQuery(SetmealPageQueryDTO setmealPageQueryDTO) {
        log.info("套餐分页查询：{}", setmealPageQueryDTO);
        PageResult<SetmealVO> pageResult = setmealService.setmealPageQuery(setmealPageQueryDTO);
        return Result.success(pageResult);
    }

    /**
     * 批量删除套餐
     */
    @DeleteMapping
    @ApiOperation("批量删除套餐")
    public Result<String> setmealDelBatch(@RequestParam List<Long> ids) {
        log.info("批量删除套餐：{}", ids);
        setmealService.setmealDelBatch(ids);
        return Result.success();
    }

    /**
     * 根据ID查询套餐
     */
    @GetMapping("/{id}")
    @ApiOperation("根据ID查询套餐")
    public Result<SetmealVO> getSetmealById(@PathVariable Long id) {
        log.info("根据ID查询套餐：{}", id);
        SetmealVO setmealVO = setmealService.getSetmealById(id);
        return Result.success(setmealVO);
    }
}
