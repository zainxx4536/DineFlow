package com.dineflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.dineflow.dto.SetmealDTO;
import com.dineflow.entity.Setmeal;
import com.dineflow.entity.SetmealDish;
import com.dineflow.exception.BaseException;
import com.dineflow.mapper.SetmealMapper;
import com.dineflow.service.ISetmealService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * <p>
 * 套餐 服务实现类
 * </p>
 *
 * @author zainxx
 * @since 2026-09-08
 */
@Service
public class SetmealServiceImpl extends ServiceImpl<SetmealMapper, Setmeal> implements ISetmealService {

    /**
     * 新增套餐
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void addSetmeal(SetmealDTO setmealDTO) {
        //套餐中菜品不能为空
        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        if (CollUtil.isEmpty(setmealDishes)) {
            throw new BaseException("套餐中菜品不能为空！");
        }
        //套餐不能重名
        boolean exists = lambdaQuery().eq(Setmeal::getName, setmealDTO.getName()).exists();
        if (exists) {
            throw new BaseException("该套餐已存在！");
        }
        //新增套餐
        Setmeal setmeal = BeanUtil.copyProperties(setmealDTO, Setmeal.class);
        save(setmeal);
        //存储套餐和菜品的对应关系
        Long setmealId = setmeal.getId();
        for (SetmealDish setmealDish : setmealDishes) {
            setmealDish.setSetmealId(setmealId);
        }
        Db.saveBatch(setmealDishes);
    }
}
