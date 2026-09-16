package com.dineflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.dineflow.dto.ShoppingCartDTO;
import com.dineflow.entity.Dish;
import com.dineflow.entity.Setmeal;
import com.dineflow.entity.ShoppingCart;
import com.dineflow.exception.BaseException;
import com.dineflow.exception.InvalidParameterException;
import com.dineflow.mapper.ShoppingCartMapper;
import com.dineflow.service.IShoppingCartService;
import com.dineflow.utils.ThreadLocalUtil;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Stream;

/**
 * <p>
 * 购物车 服务实现类
 * </p>
 *
 * @author zainxx
 * @since 2026-09-08
 */
@Service
public class ShoppingCartServiceImpl extends ServiceImpl<ShoppingCartMapper, ShoppingCart> implements IShoppingCartService {

    /**
     * 向购物车添加菜品或套餐
     */
    @Override
    public void addItemsToCart(ShoppingCartDTO shoppingCartDTO) {

        Long userId = ThreadLocalUtil.getCurrentId();

        ShoppingCart shoppingCart = BeanUtil.copyProperties(shoppingCartDTO, ShoppingCart.class);

        shoppingCart.setUserId(userId);

        // 查询购物车中是否已经存在相同菜品/套餐
        LambdaQueryWrapper<ShoppingCart> wrapper =
                new LambdaQueryWrapper<ShoppingCart>()
                        .eq(ShoppingCart::getUserId, userId)
                        .eq(shoppingCart.getSetmealId() != null,
                                ShoppingCart::getSetmealId,
                                shoppingCart.getSetmealId())
                        .eq(shoppingCart.getDishId() != null,
                                ShoppingCart::getDishId,
                                shoppingCart.getDishId());

        // 菜品还需要区分口味
        if (shoppingCart.getDishId() != null) {
            if (StrUtil.isNotBlank(shoppingCart.getDishFlavor())) {
                wrapper.eq(
                        ShoppingCart::getDishFlavor,
                        shoppingCart.getDishFlavor()
                );
            } else {
                wrapper.isNull(ShoppingCart::getDishFlavor);
            }
        }

        ShoppingCart existedCart = getOne(wrapper);

        if (existedCart != null) {
            // 已存在，数量 +1
            lambdaUpdate()
                    .eq(ShoppingCart::getId, existedCart.getId())
                    .setSql("number = number + 1")
                    .update();
        } else {
            // 不存在，新增购物车记录，还要判断添加的到底是套餐还是菜品，获取图像和价格
            // 新增购物车记录
            shoppingCart.setNumber(1);

            if (shoppingCart.getDishId() != null) {

                // 查询菜品信息
                Dish dish = Db.lambdaQuery(Dish.class)
                        .eq(Dish::getId, shoppingCart.getDishId())
                        .one();

                if (dish == null) {
                    throw new BaseException("菜品不存在");
                }

                shoppingCart.setName(dish.getName());
                shoppingCart.setImage(dish.getImage());
                shoppingCart.setAmount(dish.getPrice());

            } else if (shoppingCart.getSetmealId() != null) {

                // 查询套餐信息
                Setmeal setmeal = Db.lambdaQuery(Setmeal.class)
                        .eq(Setmeal::getId, shoppingCart.getSetmealId())
                        .one();

                if (setmeal == null) {
                    throw new BaseException("套餐不存在");
                }

                shoppingCart.setName(setmeal.getName());
                shoppingCart.setImage(setmeal.getImage());
                shoppingCart.setAmount(setmeal.getPrice());

            } else {
                throw new InvalidParameterException("菜品ID和套餐ID不能同时为空");
            }

            save(shoppingCart);
        }
    }

    /**
     * 查看购物车
     */
    @Override
    public List<ShoppingCart> showShoppingCart() {
        Long userId = ThreadLocalUtil.getCurrentId();
        return lambdaQuery()
                .eq(ShoppingCart::getUserId, userId)
                .orderByDesc(ShoppingCart::getCreateTime)
                .orderByDesc(ShoppingCart::getId)
                .list();
    }

    /**
     * 购物车中的商品数量减一
     */
    @Override
    public void subItemToCart(ShoppingCartDTO shoppingCartDTO) {

        Long userId = ThreadLocalUtil.getCurrentId();

        ShoppingCart shoppingCart = BeanUtil.copyProperties(shoppingCartDTO, ShoppingCart.class);

        shoppingCart.setUserId(userId);

        LambdaQueryWrapper<ShoppingCart> wrapper =
                new LambdaQueryWrapper<ShoppingCart>()
                        .eq(ShoppingCart::getUserId, userId)
                        .eq(shoppingCart.getSetmealId() != null,
                                ShoppingCart::getSetmealId,
                                shoppingCart.getSetmealId())
                        .eq(shoppingCart.getDishId() != null,
                                ShoppingCart::getDishId,
                                shoppingCart.getDishId());

        // 菜品还需要区分口味
        if (shoppingCart.getDishId() != null) {
            if (StrUtil.isNotBlank(shoppingCart.getDishFlavor())) {
                wrapper.eq(
                        ShoppingCart::getDishFlavor,
                        shoppingCart.getDishFlavor()
                );
            } else {
                wrapper.isNull(ShoppingCart::getDishFlavor);
            }
        }

        ShoppingCart existedCart = getOne(wrapper);

        if (existedCart == null) {
            return;
        }

        // 数量大于1，数量减1
        if (existedCart.getNumber() > 1) {
            lambdaUpdate()
                    .eq(ShoppingCart::getId, existedCart.getId())
                    .setSql("number = number - 1")
                    .update();
        } else {
            // 数量为1，直接删除购物车记录
            removeById(existedCart.getId());
        }
    }

    /**
     * 一键清空购物车
     */
    @Override
    public void cleanShoppingCart() {
        Long userId = ThreadLocalUtil.getCurrentId();

        lambdaUpdate()
                .eq(ShoppingCart::getUserId, userId)
                .remove();
    }
}
