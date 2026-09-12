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
import com.dineflow.exception.BaseException;
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
        boolean contained = Db.lambdaQuery(SetmealDish.class)
                .in(SetmealDish::getDishId, ids)
                .exists();
        if (contained) {
            throw new DeletionNotAllowedException("菜品已被套餐关联，不可删除!");
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
        Dish dish = BeanUtil.copyProperties(dishDTO, Dish.class);
        //这里会有一个业务误区，MP 默认不会更新实体中为 null 的字段，
        //  假如前面传过来的“description”为 null 的意思是没有描述而非不更新（一般都是这种意思），
        //  这里就会处理错误：任然保留旧版的description。
        //  可以在实体类中的 description 字段上加 @TableField(updateStrategy = FieldStrategy.ALWAYS) 注解
        //  这样无论前面传过来的 description 是 null 还是有内容，都会更新 description。
        updateById(dish);
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
}
