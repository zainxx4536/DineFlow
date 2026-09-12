package com.dineflow.controller.admin;


import com.dineflow.dto.DishDTO;
import com.dineflow.dto.DishPageQueryDTO;
import com.dineflow.result.PageResult;
import com.dineflow.result.Result;
import com.dineflow.service.IDishService;
import com.dineflow.vo.DishVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 菜品 前端控制器
 * </p>
 *
 * @author zainxx
 * @since 2026-09-08
 */
@RestController
@RequestMapping("/admin/dish")
@Slf4j
@RequiredArgsConstructor
@Api(tags = "菜品相关接口")
public class DishController {

    private final IDishService dishService;

    /**
     * 新增菜品
     */
    @PostMapping
    @ApiOperation("新增菜品")
    public Result<String> addDish(@RequestBody DishDTO dishDTO) {
        log.info("新增菜品：{}", dishDTO);
        dishService.addDish(dishDTO);
        return Result.success();
    }

    /**
     * 菜品分页查询
     */
    @GetMapping("/page")
    @ApiOperation("菜品分页查询")
    public Result<PageResult<DishVO>> dishPageQuery(DishPageQueryDTO dishPageQueryDTO) {
        log.info("菜品分页查询：{}", dishPageQueryDTO);
        PageResult<DishVO> pageResult = dishService.dishPageQuery(dishPageQueryDTO);
        return Result.success(pageResult);
    }

    /**
     * 批量删除菜品
     */
    @DeleteMapping
    @ApiOperation("批量删除菜品")
    public Result<String> dishDelBatch(@RequestParam List<Long> ids) {
        log.info("批量删除菜品：{}", ids);
        dishService.dishDelBatch(ids);
        return Result.success();
    }

    /**
     * 根据ID查询菜品（数据回显）
     */
    @GetMapping("/{id}")
    @ApiOperation("根据ID查询菜品")
    public Result<DishVO> getDishById(@PathVariable Long id) {
        log.info("根据ID查询菜品：{}", id);
        DishVO dishVO = dishService.getDishById(id);
        return Result.success(dishVO);
    }

    /**
     * 修改菜品信息
     */
    @PutMapping
    @ApiOperation("修改菜品信息")
    public Result<String> modifyDishInfo(@RequestBody DishDTO dishDTO) {
        log.info("修改菜品信息：{}", dishDTO);
        dishService.modifyDishInfo(dishDTO);
        return Result.success();
    }
}
