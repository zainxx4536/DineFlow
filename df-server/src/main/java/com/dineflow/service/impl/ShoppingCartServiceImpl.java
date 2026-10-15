package com.dineflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.dineflow.constant.MessageConstant;
import com.dineflow.constant.StatusConstant;
import com.dineflow.dto.ShoppingCartDTO;
import com.dineflow.dto.ShoppingCartSubDTO;
import com.dineflow.entity.Dish;
import com.dineflow.entity.Setmeal;
import com.dineflow.entity.ShoppingCart;
import com.dineflow.exception.BaseException;
import com.dineflow.exception.InvalidParameterException;
import com.dineflow.mapper.ShoppingCartMapper;
import com.dineflow.service.IShoppingCartService;
import com.dineflow.utils.CartItemKeyUtil;
import com.dineflow.utils.ThreadLocalUtil;
import com.dineflow.vo.ShoppingCartItemVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
     * 精确识别本次购物车同款唯一约束。
     */
    private static final Pattern CART_UNIQUE_CONSTRAINT = Pattern.compile(
            "for key ['`](?:[^'`]+\\.)?uk_cart_user_item['`]",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 向购物车添加菜品或套餐。
     */
    @Override
    public void addItemsToCart(ShoppingCartDTO dto) {
        Long userId = ThreadLocalUtil.getCurrentId();

        if (userId == null) {
            throw new BaseException(MessageConstant.USER_NOT_LOGIN);
        }

        if (dto == null) {
            throw new InvalidParameterException("购物车参数不能为空");
        }

        // 1. 规范化口味，并生成同款标识。
        String flavor = CartItemKeyUtil.normalizeFlavor(dto.getDishFlavor());

        String itemKey = CartItemKeyUtil.build(dto.getDishId(), dto.getSetmealId(), flavor);

        ShoppingCart cart = BeanUtil.copyProperties(dto, ShoppingCart.class);
        cart.setUserId(userId);
        cart.setDishFlavor(flavor);
        cart.setItemKey(itemKey);

        // 2. 首次加购和已有同款追加，都检查当前商品是否可售。
        if (cart.getDishId() != null) {
            Dish dish = Db.lambdaQuery(Dish.class)
                    .eq(Dish::getId, cart.getDishId())
                    .one();

            if (dish == null || !StatusConstant.ENABLE.equals(dish.getStatus())) {
                throw new InvalidParameterException("菜品不存在或已停售");
            }

            cart.setName(dish.getName());
            cart.setImage(dish.getImage());
            cart.setAmount(dish.getPrice());
        } else {
            Setmeal setmeal = Db.lambdaQuery(Setmeal.class)
                    .eq(Setmeal::getId, cart.getSetmealId())
                    .one();

            if (setmeal == null || !StatusConstant.ENABLE.equals(setmeal.getStatus())) {
                throw new InvalidParameterException("套餐不存在或已停售");
            }

            cart.setName(setmeal.getName());
            cart.setImage(setmeal.getImage());
            cart.setAmount(setmeal.getPrice());
        }

        BigDecimal price = cart.getAmount();

        if (price == null
                || price.signum() <= 0
                || price.compareTo(new BigDecimal("99999999.99")) > 0
                || price.stripTrailingZeros().scale() > 2) {
            throw new InvalidParameterException("商品价格异常");
        }

        // 3. 通过“当前用户 + item_key”查询同款记录。
        ShoppingCart existed = lambdaQuery()
                .eq(ShoppingCart::getUserId, userId)
                .eq(ShoppingCart::getItemKey, itemKey)
                .one();

        // 4. 已有同款，使用 SQL 原子加一。
        if (existed != null) {
            boolean updated = lambdaUpdate()
                    .eq(ShoppingCart::getId, existed.getId())
                    .eq(ShoppingCart::getUserId, userId)
                    .eq(ShoppingCart::getItemKey, itemKey)
                    .ge(ShoppingCart::getNumber, 1)
                    .lt(ShoppingCart::getNumber, 99)
                    .ge(ShoppingCart::getVersion, 0)
                    .lt(ShoppingCart::getVersion, Integer.MAX_VALUE)
                    .setSql("number = number + 1")
                    .setSql("version = version + 1")
                    .update();

            if (!updated) {
                throw new InvalidParameterException("单项商品最多99份，或购物车已变化，请刷新后重试");
            }

            return;
        }

        // 5. 没有同款，先检查购物车条目数。
        long rows = lambdaQuery()
                .eq(ShoppingCart::getUserId, userId)
                .count();

        if (rows >= 100) {
            throw new InvalidParameterException("购物车最多支持100条商品");
        }

        cart.setNumber(1);
        cart.setVersion(0);

        // 6. 首次插入，由数据库联合唯一约束兜底。
        try {
            int inserted = baseMapper.insert(cart);
            if (inserted != 1) {
                throw new BaseException("商品加入购物车失败");
            }
        } catch (DuplicateKeyException exception) {
            //判断是否为 uk_cart_user_item 唯一约束冲突。
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof SQLException sql
                        && sql.getErrorCode() == 1062
                        && sql.getMessage() != null
                        && CART_UNIQUE_CONSTRAINT
                        .matcher(sql.getMessage())
                        .find()) {
                    throw new InvalidParameterException("购物车已变化，请刷新后重试");
                }
            }

            // 其他唯一键异常继续抛出，避免误判。
            throw exception;
        }
    }

    /**
     * 查看购物车，同时校验购物车中商品的状态
     */
    @Override
    public List<ShoppingCartItemVO> showShoppingCart() {
        Long userId = ThreadLocalUtil.getCurrentId();

        if (userId == null) {
            throw new BaseException(MessageConstant.USER_NOT_LOGIN);
        }

        // 1. 查询用户购物车
        List<ShoppingCart> shoppingCartList = lambdaQuery()
                .eq(ShoppingCart::getUserId, userId)
                .orderByDesc(ShoppingCart::getCreateTime)
                .orderByDesc(ShoppingCart::getId)
                .list();

        if (shoppingCartList.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 分别提取菜品ID和套餐ID，Set自动去重
        Set<Long> dishIds = shoppingCartList.stream()
                .map(ShoppingCart::getDishId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<Long> setmealIds = shoppingCartList.stream()
                .map(ShoppingCart::getSetmealId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 3. 批量查询，空集合不执行IN查询
        Map<Long, Dish> dishMap = dishIds.isEmpty()
                ? Collections.emptyMap()
                : Db.lambdaQuery(Dish.class)
                .in(Dish::getId, dishIds)
                .list()
                .stream()
                .collect(Collectors.toMap(
                        Dish::getId, Function.identity()
                ));

        Map<Long, Setmeal> setmealMap = setmealIds.isEmpty()
                ? Collections.emptyMap()
                : Db.lambdaQuery(Setmeal.class)
                .in(Setmeal::getId, setmealIds)
                .list()
                .stream()
                .collect(Collectors.toMap(
                        Setmeal::getId, Function.identity()
                ));

        // 4. 组装返回结果，循环中不再查询数据库
        List<ShoppingCartItemVO> result = new ArrayList<>();

        for (ShoppingCart cart : shoppingCartList) {
            // 先保留历史名称和图片，若商品已删除时仍能识别该条目
            ShoppingCartItemVO vo = BeanUtil.copyProperties(cart, ShoppingCartItemVO.class);
            vo.setCartId(cart.getId());

            // Entity叫version，VO叫cartVersion，必须显式赋值。
            vo.setCartVersion(cart.getVersion());

            vo.setAvailable(false);
            result.add(vo);

            if (cart.getVersion() == null || cart.getVersion() < 0) {
                vo.setUnavailableReason("购物车版本异常");
                continue;
            }

            boolean hasDish = cart.getDishId() != null;
            boolean hasSetmeal = cart.getSetmealId() != null;
            // 商品类型即为菜品也为套餐 或 既不是菜品也不是套餐
            if (hasDish == hasSetmeal) {
                vo.setUnavailableReason("购物车商品类型异常");
                continue;
            }

            BigDecimal price;
            Integer status;
            if (hasDish) {
                Dish dish = dishMap.get(cart.getDishId());
                if (dish == null) {
                    vo.setUnavailableReason("菜品已删除或不存在");
                    continue;
                }
                vo.setName(dish.getName());
                vo.setImage(dish.getImage());
                price = dish.getPrice();
                status = dish.getStatus();
            } else {
                Setmeal setmeal = setmealMap.get(cart.getSetmealId());
                if (setmeal == null) {
                    vo.setUnavailableReason("套餐已删除或不存在");
                    continue;
                }
                vo.setName(setmeal.getName());
                vo.setImage(setmeal.getImage());
                price = setmeal.getPrice();
                status = setmeal.getStatus();
            }
            vo.setCurrentPrice(price);

            if (!StatusConstant.ENABLE.equals(status)) {
                vo.setUnavailableReason(hasDish ? "菜品已停售" : "套餐已停售");
                continue;
            }

            if (price == null) {
                vo.setUnavailableReason("商品价格未配置");
                continue;
            }

            if (price.signum() <= 0
                    || price.compareTo(new BigDecimal("99999999.99")) > 0
                    || price.stripTrailingZeros().scale() > 2) {
                vo.setUnavailableReason("商品价格异常");
                continue;
            }

            Integer number = cart.getNumber();

            if (number == null || number < 1 || number > 99) {
                vo.setUnavailableReason("单项商品数量必须为1～99份");
                continue;
            }

            BigDecimal subtotal = price.multiply(BigDecimal.valueOf(number));
            vo.setSubtotal(subtotal);
            vo.setAvailable(true);
        }

        return result;
    }

    /**
     * 根据购物车行ID减一，不要求商品仍然可售。
     */
    @Override
    public void subItemToCart(ShoppingCartSubDTO dto) {
        Long userId = ThreadLocalUtil.getCurrentId();

        if (userId == null) {
            throw new BaseException(MessageConstant.USER_NOT_LOGIN);
        }

        if (dto == null || dto.getCartId() == null || dto.getCartId() <= 0) {
            throw new InvalidParameterException("购物车条目ID不合法");
        }

        // 必须同时限定条目ID和当前用户。
        ShoppingCart cart = lambdaQuery()
                .eq(ShoppingCart::getId, dto.getCartId())
                .eq(ShoppingCart::getUserId, userId)
                .one();

        // 不存在或不属于本人，均不操作。
        // 不向调用方泄露他人记录是否存在。
        if (cart == null) {
            return;
        }

        Integer number = cart.getNumber();
        Integer version = cart.getVersion();

        if (number == null
                || number < 1
                || number > 99
                || version == null
                || version < 0
                || version == Integer.MAX_VALUE) {
            throw new InvalidParameterException("购物车数据异常，请移除该条目后重新加购");
        }

        boolean changed;

        if (number > 1) {
            // 查询后版本没有变化，才允许执行减一。
            changed = lambdaUpdate()
                    .eq(ShoppingCart::getId, cart.getId())
                    .eq(ShoppingCart::getUserId, userId)
                    .eq(ShoppingCart::getVersion, version)
                    .gt(ShoppingCart::getNumber, 1)
                    .setSql("number = number - 1")
                    .setSql("version = version + 1")
                    .update();
        } else {
            // 查询后仍为同一版本、数量仍为1，才允许删除。
            changed = lambdaUpdate()
                    .eq(ShoppingCart::getId, cart.getId())
                    .eq(ShoppingCart::getUserId, userId)
                    .eq(ShoppingCart::getVersion, version)
                    .eq(ShoppingCart::getNumber, 1)
                    .remove();
        }

        if (!changed) {
            throw new InvalidParameterException("购物车已变化，请刷新后重试");
        }
    }

    /**
     * 主动移除本人购物车中的整条记录。
     * 包括商品已删除、停售，以及数量或版本异常的记录。
     */
    @Override
    public void removeItemFromCart(Long cartId) {
        Long userId = ThreadLocalUtil.getCurrentId();

        if (userId == null) {
            throw new BaseException(MessageConstant.USER_NOT_LOGIN);
        }

        if (cartId == null || cartId <= 0) {
            throw new InvalidParameterException("购物车条目ID不合法");
        }

        /*
         * 用户明确要求移除整条记录，因此直接删除当前整条。
         * 不依赖商品状态、数量、版本或 item_key。
         * 已删除或不属于本人时，按无操作成功处理。
         */
        lambdaUpdate()
                .eq(ShoppingCart::getId, cartId)
                .eq(ShoppingCart::getUserId, userId)
                .remove();
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
