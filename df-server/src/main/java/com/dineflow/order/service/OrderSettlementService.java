package com.dineflow.order.service;

import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.dineflow.constant.MessageConstant;
import com.dineflow.entity.Dish;
import com.dineflow.entity.Setmeal;
import com.dineflow.entity.ShoppingCart;
import com.dineflow.exception.OrderBusinessException;
import com.dineflow.order.model.SettlementItem;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 构造订单结算项集合
 */
@Service
public class OrderSettlementService {

    /**
     * 根据购物车构造当前结算项
     */
    public List<SettlementItem> buildSettlementItems(List<ShoppingCart> cartList) {

        // 1. 收集 dishId
        List<Long> dishIds = cartList.stream()
                .map(ShoppingCart::getDishId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        // 2. 收集 setmealId
        List<Long> setmealIds = cartList.stream()
                .map(ShoppingCart::getSetmealId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        // 3. 批量查询当前菜品
        Map<Long, Dish> dishMap = dishIds.isEmpty()
                ? Collections.emptyMap()
                : Db.lambdaQuery(Dish.class)
                    .in(Dish::getId, dishIds)
                    .list()
                    .stream()
                    .collect(Collectors.toMap(
                            Dish::getId,
                            Function.identity()
                    ));

        // 4. 批量查询当前套餐
        Map<Long, Setmeal> setmealMap = setmealIds.isEmpty()
                ? Collections.emptyMap()
                : Db.lambdaQuery(Setmeal.class)
                    .in(Setmeal::getId, setmealIds)
                    .list()
                    .stream()
                    .collect(Collectors.toMap(
                            Setmeal::getId,
                            Function.identity()
                    ));

        // 5. 构造结算项
        List<SettlementItem> result = new ArrayList<>();

        for (ShoppingCart cart : cartList) {

            SettlementItem item;

            if (cart.getDishId() != null) {

                Dish dish = dishMap.get(cart.getDishId());

                if (dish == null || dish.getStatus() != 1) {
                    throw new OrderBusinessException(
                            MessageConstant.ITEM_NOT_AVAILABLE
                    );
                }

                item = SettlementItem.builder()
                        .cartId(cart.getId())
                        .cartVersion(cart.getVersion())
                        .dishId(dish.getId())
                        .name(dish.getName())
                        .image(dish.getImage())
                        .dishFlavor(cart.getDishFlavor())
                        .number(cart.getNumber())
                        .unitPrice(dish.getPrice())
                        .build();

            } else {

                Setmeal setmeal = setmealMap.get(cart.getSetmealId());

                if (setmeal == null || setmeal.getStatus() != 1) {
                    throw new OrderBusinessException(MessageConstant.ITEM_NOT_AVAILABLE);
                }

                item = SettlementItem.builder()
                        .cartId(cart.getId())
                        .cartVersion(cart.getVersion())
                        .setmealId(setmeal.getId())
                        .name(setmeal.getName())
                        .image(setmeal.getImage())
                        .number(cart.getNumber())
                        .unitPrice(setmeal.getPrice())
                        .build();
            }

            item.setSubtotal(
                    item.getUnitPrice().multiply(BigDecimal.valueOf(item.getNumber()))
            );

            result.add(item);
        }

        return result;
    }

    /**
     * 根据锁定后的当前商品构造结算项
     */
    public List<SettlementItem> buildSettlementItems(
            List<ShoppingCart> cartList,
            List<Dish> dishes,
            List<Setmeal> setmeals) {

        Map<Long, Dish> dishMap = dishes.stream()
                .collect(Collectors.toMap(
                        Dish::getId,
                        Function.identity()
                ));

        Map<Long, Setmeal> setmealMap = setmeals.stream()
                .collect(Collectors.toMap(
                        Setmeal::getId,
                        Function.identity()
                ));

        List<SettlementItem> result = new ArrayList<>();

        for (ShoppingCart cart : cartList) {

            if (cart.getDishId() != null) {

                Dish dish = dishMap.get(cart.getDishId());

                if (dish == null || dish.getStatus() != 1) {
                    throw new OrderBusinessException(
                            MessageConstant.ITEM_NOT_AVAILABLE
                    );
                }

                BigDecimal subtotal = dish.getPrice()
                        .multiply(BigDecimal.valueOf(cart.getNumber()));

                result.add(
                        SettlementItem.builder()
                                .cartId(cart.getId())
                                .cartVersion(cart.getVersion())
                                .dishId(dish.getId())
                                .name(dish.getName())
                                .image(dish.getImage())
                                .dishFlavor(cart.getDishFlavor())
                                .number(cart.getNumber())
                                .unitPrice(dish.getPrice())
                                .subtotal(subtotal)
                                .build()
                );

            } else {

                Setmeal setmeal =
                        setmealMap.get(cart.getSetmealId());

                if (setmeal == null || setmeal.getStatus() != 1) {
                    throw new OrderBusinessException(
                            MessageConstant.ITEM_NOT_AVAILABLE
                    );
                }

                BigDecimal subtotal = setmeal.getPrice()
                        .multiply(BigDecimal.valueOf(cart.getNumber()));

                result.add(
                        SettlementItem.builder()
                                .cartId(cart.getId())
                                .cartVersion(cart.getVersion())
                                .setmealId(setmeal.getId())
                                .name(setmeal.getName())
                                .image(setmeal.getImage())
                                .number(cart.getNumber())
                                .unitPrice(setmeal.getPrice())
                                .subtotal(subtotal)
                                .build()
                );
            }
        }

        return result;
    }
}
