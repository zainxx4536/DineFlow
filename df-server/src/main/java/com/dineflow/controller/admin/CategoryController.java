package com.dineflow.controller.admin;


import com.dineflow.dto.CategoryDTO;
import com.dineflow.dto.CategoryPageQueryDTO;
import com.dineflow.entity.Category;
import com.dineflow.result.PageResult;
import com.dineflow.result.Result;
import com.dineflow.service.ICategoryService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 菜品及套餐分类 前端控制器
 * </p>
 *
 * @author zainxx
 * @since 2026-09-08
 */
@RestController
@RequestMapping("/admin/category")
@Api(tags = "分类相关接口")
@Slf4j
@RequiredArgsConstructor
public class CategoryController {

    private final ICategoryService categoryService;

    /**
     * 新增分类
     */
    @PostMapping
    @ApiOperation("新增分类")
    public Result<String> addNewCategory(@RequestBody CategoryDTO categoryDTO) {
        log.info("新增分类：{}", categoryDTO);
        categoryService.addNewCategory(categoryDTO);
        return Result.success();
    }

    /**
     * 分类分页查询
     */
    @GetMapping("/page")
    @ApiOperation("分类分页查询")
    public Result<PageResult<Category>> categoryPageQuery(CategoryPageQueryDTO categoryPageQueryDTO) {
        log.info("分类分页查询：{}", categoryPageQueryDTO);
        PageResult<Category> pageResult = categoryService.categoryPageQuery(categoryPageQueryDTO);
        return Result.success(pageResult);
    }

    /**
     * 根据id删除分类
     */
    @DeleteMapping
    @ApiOperation("根据id删除分类")
    public Result<String> delCategoryById(Long id) {
        log.info("根据id删除分类：{}", id);
        categoryService.delCategoryById(id);
        return Result.success();
    }

    /**
     * 修改分类
     */
    @PutMapping
    @ApiOperation("修改分类")
    public Result<String> modifyCategory(@RequestBody CategoryDTO categoryDTO) {
        log.info("修改分类：{}", categoryDTO);
        categoryService.modifyCategory(categoryDTO);
        return Result.success();
    }

    /**
     * 修改分类状态
     */
    @PostMapping("/status/{status}")
    @ApiOperation("修改分类状态")
    public Result<String> modifyCategoryStatus(@PathVariable Integer status, Long id) {
        log.info("修改分类状态：{}，{}", id, status);
        categoryService.modifyCategoryStatus(id, status);
        return Result.success();
    }

    /**
     * 根据类型查询分类
     */
    @GetMapping("/list")
    @ApiOperation("根据类型查询分类")
    public Result<List<Category>> getCategoryByType(Integer type) {
        log.info("根据类型查询分类：{}", type);
        List<Category> list = categoryService.getCategoryByType(type);
        return Result.success(list);
    }
}
