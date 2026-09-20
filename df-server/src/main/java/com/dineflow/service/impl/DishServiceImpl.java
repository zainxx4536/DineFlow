package com.dineflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.dineflow.constant.MessageConstant;
import com.dineflow.constant.RedisKeyConstant;
import com.dineflow.constant.StatusConstant;
import com.dineflow.dto.DishDTO;
import com.dineflow.dto.DishPageQueryDTO;
import com.dineflow.entity.Category;
import com.dineflow.entity.Dish;
import com.dineflow.entity.DishFlavor;
import com.dineflow.entity.SetmealDish;
import com.dineflow.exception.BaseException;
import com.dineflow.exception.DeletionNotAllowedException;
import com.dineflow.exception.InvalidParameterException;
import com.dineflow.mapper.DishMapper;
import com.dineflow.result.PageResult;
import com.dineflow.service.IDishService;
import com.dineflow.vo.DishVO;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

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
            throw new DeletionNotAllowedException(MessageConstant.DISH_ON_SALE);
        }

        //再判断是否有套餐中包含的菜品，若有则不可删除
        boolean contained = Db.lambdaQuery(SetmealDish.class)
                .in(SetmealDish::getDishId, ids)
                .exists();
        if (contained) {
            throw new DeletionNotAllowedException(MessageConstant.DISH_BE_RELATED_BY_SETMEAL);
        }

        //删除菜品
        removeBatchByIds(ids);

        //删除菜品对应口味
        Db.lambdaUpdate(DishFlavor.class).in(DishFlavor::getDishId, ids).remove();
    }

    /**
     * 根据ID查询菜品
     */
    @Override
    public DishVO getDishById(Long id) {
        Dish dish = getById(id);
        //非空判断（避免 NPE 空指针异常）
        if (dish == null) {
            throw new BaseException("菜品不存在");
        }

        List<DishFlavor> dishFlavors = Db.lambdaQuery(DishFlavor.class).eq(DishFlavor::getDishId, id).list();
        Category category = Db.lambdaQuery(Category.class).eq(Category::getId, dish.getCategoryId()).one();
        DishVO dishVO = BeanUtil.copyProperties(dish, DishVO.class);
        dishVO.setFlavors(dishFlavors);

        if (category != null) {
            dishVO.setCategoryName(category.getName());
        }

        return dishVO;
    }

    /**
     * 修改菜品信息
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void modifyDishInfo(DishDTO dishDTO) {
        //非空判断（避免 NPE 空指针异常）
        Long dishId = dishDTO.getId();
        if (dishId == null) {
            throw new BaseException("菜品ID不能为空");
        }
        //更新菜品表
        Dish dish = new Dish();
        LambdaUpdateWrapper<Dish> wrapper =
                Wrappers.lambdaUpdate(Dish.class)
                        .eq(Dish::getId, dishId)
                        .set(Dish::getName, dishDTO.getName())
                        .set(Dish::getCategoryId, dishDTO.getCategoryId())
                        .set(Dish::getPrice, dishDTO.getPrice())
                        .set(Dish::getImage, dishDTO.getImage())
                        .set(Dish::getDescription, dishDTO.getDescription());
        update(dish, wrapper);
        //更新口味表(先删除原口味表中对应数据，再添加进去)
        Db.lambdaUpdate(DishFlavor.class).eq(DishFlavor::getDishId, dishId).remove();
        List<DishFlavor> flavors = dishDTO.getFlavors();
        if (CollUtil.isNotEmpty(flavors)) {
            for (DishFlavor flavor : flavors) {
                flavor.setDishId(dishId);
            }
            Db.saveBatch(flavors);
        }
    }

    /**
     * 根据分类ID查询菜品（用于添加套餐时展示可添加菜品，所以菜品状态必须为 enable）
     */
    @Override
    public List<Dish> getDishByCategoryId(Long categoryId) {
        if (categoryId == null) {
            throw new InvalidParameterException("分类ID不能为空");
        }

        return lambdaQuery().eq(Dish::getCategoryId, categoryId)
                .eq(Dish::getStatus, StatusConstant.ENABLE)
                .list();
    }

    /**
     * 修改菜品状态
     */
    @Override
    public void modifyDishStatus(Long id, Integer status) {
        Dish dish = new Dish();
        LambdaUpdateWrapper<Dish> wrapper = new LambdaUpdateWrapper<Dish>()
                .set(Dish::getStatus, status)
                .eq(Dish::getId, id);
        update(dish, wrapper);
    }

    /**
     * C端-根据分类ID查询菜品及口味
     */
    @Override
    @Cacheable(
            cacheNames = RedisKeyConstant.CATEGORY_DISH_FLAVOR,
            key = "#categoryId"
    )
    public List<DishVO> getDishAndFlavorByCategoryId(Long categoryId) {

        // 查询当前分类下起售的菜品
        List<Dish> dishList = lambdaQuery()
                .eq(Dish::getCategoryId, categoryId)
                .eq(Dish::getStatus, StatusConstant.ENABLE)
                .list();

        if (CollUtil.isEmpty(dishList)) {
            return Collections.emptyList();
        }

        // 获取所有菜品ID
        List<Long> dishIds = dishList.stream()
                .map(Dish::getId)
                .collect(Collectors.toList());

        // 一次性查询所有菜品口味
        List<DishFlavor> flavorList = Db.lambdaQuery(DishFlavor.class)
                .in(DishFlavor::getDishId, dishIds)
                .list();

        // 按 dishId 对口味进行分组
        Map<Long, List<DishFlavor>> flavorMap = flavorList.stream()
                .collect(Collectors.groupingBy(DishFlavor::getDishId));

        // 封装VO
        List<DishVO> dishVOList = new ArrayList<>();

        for (Dish dish : dishList) {
            DishVO dishVO = BeanUtil.copyProperties(dish, DishVO.class);

            dishVO.setFlavors(
                    flavorMap.getOrDefault(
                            dish.getId(), Collections.emptyList()
                    )
            );

            dishVOList.add(dishVO);
        }

        return dishVOList;
    }
}
