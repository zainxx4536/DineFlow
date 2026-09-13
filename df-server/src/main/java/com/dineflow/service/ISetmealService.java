package com.dineflow.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.dineflow.dto.SetmealDTO;
import com.dineflow.dto.SetmealPageQueryDTO;
import com.dineflow.entity.Setmeal;
import com.dineflow.result.PageResult;
import com.dineflow.vo.SetmealVO;

import java.util.List;

/**
 * <p>
 * 套餐 服务类
 * </p>
 *
 * @author zainxx
 * @since 2026-09-08
 */
public interface ISetmealService extends IService<Setmeal> {

    void addSetmeal(SetmealDTO setmealDTO);

    PageResult<SetmealVO> setmealPageQuery(SetmealPageQueryDTO setmealPageQueryDTO);

    void setmealDelBatch(List<Long> ids);

    SetmealVO getSetmealById(Long id);
}
