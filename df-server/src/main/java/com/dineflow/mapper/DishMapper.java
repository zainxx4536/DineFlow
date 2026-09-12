package com.dineflow.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dineflow.dto.DishPageQueryDTO;
import com.dineflow.entity.Dish;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dineflow.vo.DishVO;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 * 菜品 Mapper 接口
 * </p>
 *
 * @author zainxx
 * @since 2026-09-08
 */
public interface DishMapper extends BaseMapper<Dish> {

    Page<DishVO> dishPageQuery(Page<DishVO> page, @Param("dto") DishPageQueryDTO dishPageQueryDTO);
}
