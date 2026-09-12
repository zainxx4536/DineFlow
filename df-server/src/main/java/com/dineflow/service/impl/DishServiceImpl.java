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
import com.dineflow.mapper.DishMapper;
import com.dineflow.result.PageResult;
import com.dineflow.service.IDishService;
import com.dineflow.vo.DishVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

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
    @Transactional
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
        /*
        //构造分页条件
        int pageNo = dishPageQueryDTO.getPage();
        int pageSize = dishPageQueryDTO.getPageSize();
        Page<Dish> page = Page.of(pageNo, pageSize);
        page.addOrder(new OrderItem().setColumn("update_time").setAsc(false));
        page.addOrder(new OrderItem().setColumn("id").setAsc(true));
        LambdaQueryWrapper<Dish> wrapper = new LambdaQueryWrapper<Dish>()
                .like(StrUtil.isNotBlank(dishPageQueryDTO.getName()), Dish::getName, dishPageQueryDTO.getName())
                .eq(dishPageQueryDTO.getCategoryId() != null, Dish::getCategoryId, dishPageQueryDTO.getCategoryId())
                .eq(dishPageQueryDTO.getStatus() != null, Dish::getStatus, dishPageQueryDTO.getStatus());
        //进行分页查询
        Page<Dish> p = page(page, wrapper);
        //数据组装
        List<Dish> records = p.getRecords();
        List<DishVO> dishVOS = new ArrayList<>();
        for (Dish record : records) {
            DishVO dishVO = BeanUtil.copyProperties(record, DishVO.class);
            Category category = Db.lambdaQuery(Category.class).eq(Category::getId, dishVO.getCategoryId()).one();
            dishVO.setCategoryName(category.getName());
            dishVOS.add(dishVO);
        }
        return new PageResult<>(p.getTotal(), dishVOS);
        */

        //上面这种写法要执行的 SQL 语句很多，下面是一种优化的写法，即将这种复杂查询（左连接）放到 mapper 层中
        int pageNo = dishPageQueryDTO.getPage();
        int pageSize = dishPageQueryDTO.getPageSize();
        Page<DishVO> page = Page.of(pageNo, pageSize);
        Page<DishVO> p = dishMapper.dishPageQuery(page, dishPageQueryDTO);
        return new PageResult<>(p.getTotal(), p.getRecords());
    }
}
