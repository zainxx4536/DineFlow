package com.dineflow.controller.user;

import com.dineflow.entity.Category;
import com.dineflow.result.Result;
import com.dineflow.service.ICategoryService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController("userCategoryController")
@RequestMapping("/user/category")
@Api(tags = "C端-分类接口")
@Slf4j
@RequiredArgsConstructor
public class CategoryController {

    private final ICategoryService categoryService;

    /**
     * 根据类型查询分类，当不传类型时查询所有分类
     */
    @GetMapping("/list")
    @ApiOperation("根据类型查询分类")
    public Result<List<Category>> categoryQueryByType(Integer type) {
        log.info("根据类型查询分类：{}", type);
        List<Category> list = categoryService.categoryQueryByType(type);
        return Result.success(list);
    }
}
