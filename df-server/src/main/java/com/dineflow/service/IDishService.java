package com.dineflow.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.dineflow.dto.DishDTO;
import com.dineflow.dto.DishPageQueryDTO;
import com.dineflow.entity.Dish;
import com.dineflow.result.PageResult;
import com.dineflow.vo.DishVO;

import java.util.List;

/**
 * <p>
 * 菜品 服务类
 * </p>
 *
 * @author zainxx
 * @since 2026-09-08
 */
public interface IDishService extends IService<Dish> {

    void addDish(DishDTO dishDTO);

    PageResult<DishVO> dishPageQuery(DishPageQueryDTO dishPageQueryDTO);

    void dishDelBatch(List<Long> ids);

    DishVO getDishById(Long id);

    void modifyDishInfo(DishDTO dishDTO);
}
