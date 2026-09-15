package com.dineflow.controller.user;

import com.dineflow.result.Result;
import com.dineflow.service.IDishService;
import com.dineflow.vo.DishVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController("userDishController")
@RequestMapping("/user/dish")
@Slf4j
@Api(tags = "C端-菜品浏览接口")
@RequiredArgsConstructor
public class DishController {

    private final IDishService dishService;

    /**
     * 根据分类ID查询菜品及口味
     */
    @GetMapping("/list")
    @ApiOperation("根据分类ID查询菜品及口味")
    public Result<List<DishVO>> getDishAndFlavorByCategoryId(Long categoryId) {
        log.info("根据分类ID查询菜品及口味:{}", categoryId);
        List<DishVO> dishVOList = dishService.getDishAndFlavorByCategoryId(categoryId);
        return Result.success(dishVOList);
    }
}
