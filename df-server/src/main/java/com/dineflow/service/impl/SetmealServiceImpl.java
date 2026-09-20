package com.dineflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.dineflow.constant.MessageConstant;
import com.dineflow.constant.RedisKeyConstant;
import com.dineflow.constant.StatusConstant;
import com.dineflow.dto.SetmealDTO;
import com.dineflow.dto.SetmealPageQueryDTO;
import com.dineflow.entity.Category;
import com.dineflow.entity.Dish;
import com.dineflow.entity.Setmeal;
import com.dineflow.entity.SetmealDish;
import com.dineflow.exception.BaseException;
import com.dineflow.exception.DeletionNotAllowedException;
import com.dineflow.exception.InvalidParameterException;
import com.dineflow.exception.SetmealEnableFailedException;
import com.dineflow.mapper.SetmealMapper;
import com.dineflow.result.PageResult;
import com.dineflow.service.ISetmealService;
import com.dineflow.vo.DishItemVO;
import com.dineflow.vo.SetmealVO;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 套餐 服务实现类
 * </p>
 *
 * @author zainxx
 * @since 2026-09-08
 */
@Service
@RequiredArgsConstructor
public class SetmealServiceImpl extends ServiceImpl<SetmealMapper, Setmeal> implements ISetmealService {

    private final SetmealMapper setmealMapper;

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

    /**
     * 套餐分页查询
     */
    @Override
    public PageResult<SetmealVO> setmealPageQuery(SetmealPageQueryDTO dto) {
        //构建分页条件
        int pageNo = dto.getPage();
        int pageSize = dto.getPageSize();
        Page<SetmealVO> page = Page.of(pageNo, pageSize);
        //进行分页查询
        Page<SetmealVO> p = setmealMapper.setmealPageQuery(page, dto);
        return new PageResult<>(p.getTotal(), p.getRecords());
    }

    /**
     * 批量删除套餐
     */
    @Override
    public void setmealDelBatch(List<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            throw new InvalidParameterException("请选择要删除套餐!");
        }

        boolean exists = lambdaQuery()
                .in(Setmeal::getId, ids)
                .eq(Setmeal::getStatus, StatusConstant.ENABLE)
                .exists();
        if (exists) {
            throw new DeletionNotAllowedException(MessageConstant.SETMEAL_ON_SALE);
        }
        //删除套餐
        removeByIds(ids);
        //删除套餐对应菜品表中的数据
        Db.lambdaUpdate(SetmealDish.class)
                .in(SetmealDish::getSetmealId, ids)
                .remove();
    }

    /**
     * 根据ID查询套餐
     */
    @Override
    public SetmealVO getSetmealById(Long id) {
        if (id == null) {
            throw new InvalidParameterException("套餐ID不能为空！");
        }

        // 查询套餐信息
        Setmeal setmeal = getById(id);
        //查询分类信息
        Category category = Db.lambdaQuery(Category.class).eq(Category::getId, setmeal.getCategoryId()).one();
        //查询套餐和菜品对应关系
        List<SetmealDish> setmealDishList = Db.lambdaQuery(SetmealDish.class).eq(SetmealDish::getSetmealId, id).list();
        //构建返回 VO 数据
        SetmealVO setmealVO = BeanUtil.copyProperties(setmeal, SetmealVO.class);
        setmealVO.setCategoryName(category.getName());
        setmealVO.setSetmealDishes(setmealDishList);
        return setmealVO;
    }

    /**
     * 修改套餐
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void modifySetmeal(SetmealDTO setmealDTO) {

        Long setmealId = setmealDTO.getId();

        // 套餐中菜品不能为空
        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        if (CollUtil.isEmpty(setmealDishes)) {
            throw new BaseException("套餐中菜品不能为空！");
        }

        // 套餐名称不能与其他套餐重复
        boolean exists = lambdaQuery()
                .eq(Setmeal::getName, setmealDTO.getName())
                .ne(Setmeal::getId, setmealId)
                .exists();

        if (exists) {
            throw new BaseException("该套餐已存在！");
        }

        // 修改套餐
        Setmeal setmeal = new Setmeal();

        LambdaUpdateWrapper<Setmeal> wrapper =
                new LambdaUpdateWrapper<Setmeal>()
                        .eq(Setmeal::getId, setmealId)
                        .set(Setmeal::getCategoryId, setmealDTO.getCategoryId())
                        .set(Setmeal::getDescription, setmealDTO.getDescription())
                        .set(Setmeal::getImage, setmealDTO.getImage())
                        .set(Setmeal::getName, setmealDTO.getName())
                        .set(Setmeal::getPrice, setmealDTO.getPrice())
                        .set(Setmeal::getStatus, setmealDTO.getStatus());

        update(setmeal, wrapper);

        // 删除原套餐菜品关系
        Db.lambdaUpdate(SetmealDish.class)
                .eq(SetmealDish::getSetmealId, setmealId)
                .remove();

        // 添加新的套餐菜品关系
        setmealDishes.forEach(
                setmealDish -> setmealDish.setSetmealId(setmealId)
        );

        Db.saveBatch(setmealDishes);
    }

    /**
     * 修改套餐状态
     */
    @Override
    public void modifySetmealStatus(Long id, Integer status) {

        //起售套餐时需要检查套餐中的菜品状态
        if (Objects.equals(status, StatusConstant.ENABLE)) {

            List<Long> dishIds = Db.lambdaQuery(SetmealDish.class)
                    .select(SetmealDish::getDishId)
                    .eq(SetmealDish::getSetmealId, id)
                    .list()
                    .stream()
                    .map(SetmealDish::getDishId)
                    .collect(Collectors.toList());

            //套餐中存在停售菜品，则套餐不能起售
            boolean exists = Db.lambdaQuery(Dish.class)
                    .in(Dish::getId, dishIds)
                    .eq(Dish::getStatus, StatusConstant.DISABLE)
                    .exists();

            if (exists) {
                throw new SetmealEnableFailedException(MessageConstant.SETMEAL_ENABLE_FAILED);
            }
        }

        Setmeal setmeal = Setmeal.builder()
                .id(id)
                .status(status)
                .build();

        updateById(setmeal);
    }

    /**
     * C端-根据分类ID查询套餐
     */
    @Override
    @Cacheable(
            cacheNames = RedisKeyConstant.CATEGORY_SETMEAL,
            key = "#categoryId"
    )
    public List<Setmeal> getSetmealByCategoryId(Long categoryId) {
        List<Setmeal> setmealList = lambdaQuery()
                .eq(Setmeal::getCategoryId, categoryId)
                .eq(Setmeal::getStatus, StatusConstant.ENABLE)
                .list();
        if (CollUtil.isEmpty(setmealList)) {
            return Collections.emptyList();
        }
        return setmealList;
    }

    /**
     * C端-根据套餐ID查询包含菜品
     */
    @Override
    @Cacheable(
            cacheNames = RedisKeyConstant.SETMEAL_DISH,
            key = "#id"
    )
    public List<DishItemVO> getDishBySetmealId(Long id) {

        // 查询套餐与菜品的关联关系
        List<SetmealDish> setmealDishList = Db
                .lambdaQuery(SetmealDish.class)
                .eq(SetmealDish::getSetmealId, id)
                .list();

        if (CollUtil.isEmpty(setmealDishList)) {
            return Collections.emptyList();
        }

        // 获取套餐中的所有菜品ID
        List<Long> dishIdList = setmealDishList.stream()
                .map(SetmealDish::getDishId)
                .toList();

        // 一次性查询菜品信息
        List<Dish> dishList = Db.lambdaQuery(Dish.class)
                .in(Dish::getId, dishIdList)
                .list();

        // 把 List 集合转成 Map：dishId -> Dish 的形式，方便后面数据封装
        Map<Long, Dish> dishMap = dishList.stream()
                .collect(Collectors.toMap(
                        Dish::getId,
                        dish -> dish
                ));

        // 封装返回数据
        List<DishItemVO> dishItemVOS = new ArrayList<>();

        for (SetmealDish setmealDish : setmealDishList) {

            DishItemVO dishItemVO = BeanUtil.copyProperties(setmealDish, DishItemVO.class);

            Dish dish = dishMap.get(setmealDish.getDishId());

            if (dish != null) {
                dishItemVO.setImage(dish.getImage());
                dishItemVO.setDescription(dish.getDescription());
            }

            dishItemVOS.add(dishItemVO);
        }

        return dishItemVOS;
    }
}
