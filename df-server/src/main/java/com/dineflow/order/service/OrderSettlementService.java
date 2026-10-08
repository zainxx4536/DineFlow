package com.dineflow.order.service;

import static com.dineflow.constant.BusinessErrorCode.*;
import static com.dineflow.order.support.SettlementErrors.error;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dineflow.entity.*;
import com.dineflow.mapper.*;
import com.dineflow.order.model.SettlementItem;
import com.dineflow.order.support.FlavorSelection;
import com.dineflow.order.support.SettlementValidation;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.*;

/** 在目录共享锁保护下批量构建当前成交快照，不使用购物车缓存价格。 */
@Service
@RequiredArgsConstructor
public class OrderSettlementService {
    private final DishMapper dishes;
    private final SetmealMapper setmeals;
    private final SetmealDishMapper relations;
    private final DishFlavorMapper flavors;
    private final FlavorSelection selection;
    private final ObjectMapper json;

    public List<SettlementItem> buildSettlementItems(List<ShoppingCart> carts) {
        return build(carts, false);
    }

    public List<SettlementItem> buildLockedSettlementItems(List<ShoppingCart> carts) {
        return build(carts, true);
    }

    private List<SettlementItem> build(List<ShoppingCart> carts, boolean lock) {
        if (carts == null || carts.isEmpty() || carts.size() > 100) throw error(INVALID_PARAMETER);
        for (ShoppingCart cart : carts) {
            if ((cart.getDishId() == null) == (cart.getSetmealId() == null))
                throw error(INVALID_CART_ITEM);
            SettlementValidation.quantity(cart.getNumber());
        }
        String suffix = lock ? "FOR UPDATE" : "";
        // TreeSet 同时去重和排序，让不同请求按相同顺序读取/锁定商品。
        Set<Long> setIds = new TreeSet<>();
        for (ShoppingCart cart : carts) {
            if (cart.getSetmealId() != null) {
                setIds.add(cart.getSetmealId());
            }
        }
        List<Setmeal> meals =
                setIds.isEmpty()
                        ? List.of()
                        : setmeals.selectList(
                                new LambdaQueryWrapper<Setmeal>()
                                        .in(Setmeal::getId, setIds)
                                        .orderByAsc(Setmeal::getId)
                                        .last(suffix));
        List<SetmealDish> parts =
                setIds.isEmpty()
                        ? List.of()
                        : relations.selectList(
                                new LambdaQueryWrapper<SetmealDish>()
                                        .in(SetmealDish::getSetmealId, setIds)
                                        .orderByAsc(
                                                SetmealDish::getSetmealId,
                                                SetmealDish::getDishId,
                                                SetmealDish::getId)
                                        .last(suffix));
        Set<Long> dishIds = new TreeSet<>();
        for (ShoppingCart cart : carts) {
            if (cart.getDishId() != null) {
                dishIds.add(cart.getDishId());
            }
        }
        for (SetmealDish part : parts) {
            if (!SettlementValidation.positive(part.getDishId())) throw error(ITEM_NOT_AVAILABLE);
            SettlementValidation.quantity(part.getCopies());
            dishIds.add(part.getDishId());
        }
        List<Dish> dishList =
                dishIds.isEmpty()
                        ? List.of()
                        : dishes.selectList(
                                new LambdaQueryWrapper<Dish>()
                                        .in(Dish::getId, dishIds)
                                        .orderByAsc(Dish::getId)
                                        .last(suffix));
        List<DishFlavor> flavorList =
                dishIds.isEmpty()
                        ? List.of()
                        : flavors.selectList(
                                new LambdaQueryWrapper<DishFlavor>()
                                        .in(DishFlavor::getDishId, dishIds)
                                        .orderByAsc(DishFlavor::getDishId, DishFlavor::getId)
                                        .last(suffix));
        // 批量查完后放进 Map，后面按 ID 取商品，避免每个购物车条目单独查数据库。
        Map<Long, Dish> dishMap = new HashMap<>();
        for (Dish dish : dishList) {
            dishMap.put(dish.getId(), dish);
        }
        Map<Long, Setmeal> mealMap = new HashMap<>();
        for (Setmeal meal : meals) {
            mealMap.put(meal.getId(), meal);
        }
        List<SettlementItem> result = new ArrayList<>();
        for (ShoppingCart cart : carts) {
            SettlementItem item =
                    SettlementItem.builder()
                            .cartId(cart.getId())
                            .cartVersion(cart.getVersion())
                            .number(cart.getNumber())
                            .build();
            if (cart.getDishId() != null) {
                Dish dish = dishMap.get(cart.getDishId());
                checkDish(dish);
                List<DishFlavor> groups = new ArrayList<>();
                for (DishFlavor flavor : flavorList) {
                    if (Objects.equals(flavor.getDishId(), dish.getId())) {
                        groups.add(flavor);
                    }
                }
                item.setDishId(dish.getId());
                item.setName(dish.getName());
                item.setImage(dish.getImage());
                item.setUnitPrice(dish.getPrice());
                item.setDishFlavor(selection.validate(cart.getDishFlavor(), groups));
                item.setFlavorDefinition(selection.definition(groups));
            } else {
                Setmeal meal = mealMap.get(cart.getSetmealId());
                if (meal == null
                        || !Objects.equals(meal.getStatus(), 1)
                        || !selection.normalize(cart.getDishFlavor()).isEmpty())
                    throw error(ITEM_NOT_AVAILABLE);
                List<SetmealDish> children = new ArrayList<>();
                for (SetmealDish part : parts) {
                    if (Objects.equals(part.getSetmealId(), meal.getId())) {
                        children.add(part);
                    }
                }
                if (children.isEmpty()) throw error(ITEM_NOT_AVAILABLE);
                List<Map<String, Object>> snapshot = new ArrayList<Map<String, Object>>();
                Set<Long> seen = new HashSet<Long>();
                for (SetmealDish child : children) {
                    Dish dish = dishMap.get(child.getDishId());
                    checkDish(dish);
                    if (!seen.add(dish.getId())) throw error(ITEM_NOT_AVAILABLE);
                    Map<String, Object> row = new TreeMap<>();
                    row.put("dishId", dish.getId());
                    row.put("name", dish.getName());
                    row.put("image", dish.getImage());
                    row.put("copies", child.getCopies());
                    snapshot.add(row);
                }
                try {
                    item.setSetmealItemsSnapshot(json.writeValueAsString(snapshot));
                } catch (java.io.IOException e) {
                    throw new IllegalStateException("套餐快照编码失败", e);
                }
                item.setSetmealId(meal.getId());
                item.setName(meal.getName());
                item.setImage(meal.getImage());
                item.setUnitPrice(meal.getPrice());
                item.setDishFlavor("");
            }
            item.setUnitPrice(SettlementValidation.money(item.getUnitPrice()));
            item.setSubtotal(
                    SettlementValidation.money(
                            item.getUnitPrice()
                                    .multiply(java.math.BigDecimal.valueOf(item.getNumber()))));
            result.add(item);
        }
        return result;
    }

    private void checkDish(Dish dish) {
        if (dish == null || !Objects.equals(dish.getStatus(), 1)) throw error(ITEM_NOT_AVAILABLE);
        SettlementValidation.money(dish.getPrice());
    }
}
