package com.dineflow.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.dineflow.entity.DishFlavor;
import com.dineflow.mapper.DishFlavorMapper;
import com.dineflow.service.IDishFlavorService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 菜品口味关系表 服务实现类
 * </p>
 *
 * @author zainxx
 * @since 2026-09-08
 */
@Service
public class DishFlavorServiceImpl extends ServiceImpl<DishFlavorMapper, DishFlavor> implements IDishFlavorService {

}
