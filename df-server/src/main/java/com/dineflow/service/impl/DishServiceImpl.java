package com.dineflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.dineflow.constant.StatusConstant;
import com.dineflow.dto.DishDTO;
import com.dineflow.dto.DishPageQueryDTO;
import com.dineflow.entity.Category;
import com.dineflow.entity.Dish;
import com.dineflow.entity.DishFlavor;
import com.dineflow.entity.SetmealDish;
import com.dineflow.exception.DeletionNotAllowedException;
import com.dineflow.exception.InvalidParameterException;
import com.dineflow.mapper.DishMapper;
import com.dineflow.result.PageResult;
import com.dineflow.service.IDishService;
import com.dineflow.vo.DishVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * <p>
 * 菜品 服务实现类
 * </p>
 *
 * @author zainxx
 * @since 2026-09-08
 */
@Service
@RequiredArgsConstructor
public class DishServiceImpl extends ServiceImpl<DishMapper, Dish> implements IDishService {

    private final DishMapper dishMapper;

    /**
     * 新增菜品
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void addDish(DishDTO dishDTO) {
        Dish dish = BeanUtil.copyProperties(dishDTO, Dish.class);
        dish.setStatus(StatusConstant.ENABLE);
        save(dish);
        Long dishId = dish.getId();
        List<DishFlavor> flavors = dishDTO.getFlavors();
        if (CollUtil.isNotEmpty(flavors)) {
            for (DishFlavor flavor : flavors) {
                flavor.setDishId(dishId);
            }
            Db.saveBatch(flavors);
        }
    }

    /**
     * 菜品分页查询
     */
    @Override
    public PageResult<DishVO> dishPageQuery(DishPageQueryDTO dishPageQueryDTO) {
        //将这种复杂查询（左连接）放到 mapper 层中
        int pageNo = dishPageQueryDTO.getPage();
        int pageSize = dishPageQueryDTO.getPageSize();
        Page<DishVO> page = Page.of(pageNo, pageSize);
        Page<DishVO> p = dishMapper.dishPageQuery(page, dishPageQueryDTO);
        return new PageResult<>(p.getTotal(), p.getRecords());
    }

    /**
     * 批量删除菜品
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void dishDelBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new InvalidParameterException("待删除菜品ID不能为空!");
        }

        //要先判断菜品是否启用，若启用则不可删除
        boolean enable = lambdaQuery()
                .in(Dish::getId, ids)
                .eq(Dish::getStatus, StatusConstant.ENABLE)
                .exists();
        if (enable) {
            throw new DeletionNotAllowedException("起售中的菜品不可删除!");
        }

        //再判断是否有套餐中包含的菜品，若有则不可删除
        boolean contained = Db.lambdaQuery(SetmealDish.class).in(SetmealDish::getDishId, ids).exists();
        if (contained) {
            throw new DeletionNotAllowedException("菜品已被套餐关联，不可删除!");
        }

        //删除菜品
        removeBatchByIds(ids);

        //删除菜品对应口味
        Db.lambdaUpdate(DishFlavor.class).in(DishFlavor::getDishId, ids).remove();
    }
}
