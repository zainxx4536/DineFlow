package com.dineflow.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.dineflow.dto.DishDTO;
import com.dineflow.entity.Dish;

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
}
