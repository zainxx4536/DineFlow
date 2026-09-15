package com.dineflow.controller.user;

import com.dineflow.entity.Setmeal;
import com.dineflow.result.Result;
import com.dineflow.service.ISetmealService;
import com.dineflow.vo.DishItemVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController("userSetmealController")
@RequestMapping("/user/setmeal")
@Api(tags = "C端-套餐浏览接口")
@Slf4j
@RequiredArgsConstructor
public class SetmealController {

    private final ISetmealService setmealService;

    /**
     * 根据分类ID查询套餐
     */
    @GetMapping("/list")
    @ApiOperation("根据分类ID查询套餐")
    public Result<List<Setmeal>> getSetmealByCategoryId(Long categoryId) {
        log.info("根据分类id查询套餐: {}", categoryId);
        List<Setmeal> setmealList = setmealService.getSetmealByCategoryId(categoryId);
        return Result.success(setmealList);
    }

    /**
     * 根据套餐ID查询包含的菜品
     */
    @GetMapping("/dish/{id}")
    @ApiOperation("根据套餐ID查询包含的菜品")
    public Result<List<DishItemVO>> getDishBySetmealId(@PathVariable Long id) {
        log.info("根据套餐ID查询包含的菜品：{}", id);
        List<DishItemVO> dishItemVOList = setmealService.getDishBySetmealId(id);
        return Result.success(dishItemVOList);
    }
}
