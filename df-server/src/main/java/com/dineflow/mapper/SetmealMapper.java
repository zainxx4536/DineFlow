package com.dineflow.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dineflow.dto.SetmealPageQueryDTO;
import com.dineflow.entity.Setmeal;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dineflow.vo.SetmealOverViewVO;
import com.dineflow.vo.SetmealVO;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 * 套餐 Mapper 接口
 * </p>
 *
 * @author zainxx
 * @since 2026-09-08
 */
public interface SetmealMapper extends BaseMapper<Setmeal> {

    Page<SetmealVO> setmealPageQuery(@Param("page") Page<SetmealVO> page, @Param("dto") SetmealPageQueryDTO dto);

    SetmealOverViewVO getSetmealOverView();
}
