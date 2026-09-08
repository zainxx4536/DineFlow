package com.dineflow.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.dineflow.entity.Dish;
import com.dineflow.mapper.DishMapper;
import com.dineflow.service.IDishService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 菜品 服务实现类
 * </p>
 *
 * @author zainxx
 * @since 2026-09-08
 */
@Service
public class DishServiceImpl extends ServiceImpl<DishMapper, Dish> implements IDishService {

}
