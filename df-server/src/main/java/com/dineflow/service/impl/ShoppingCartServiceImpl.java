package com.dineflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.dineflow.constant.MessageConstant;
import com.dineflow.constant.StatusConstant;
import com.dineflow.dto.ShoppingCartDTO;
import com.dineflow.entity.Dish;
import com.dineflow.entity.Setmeal;
import com.dineflow.entity.ShoppingCart;
import com.dineflow.exception.BaseException;
import com.dineflow.exception.InvalidParameterException;
import com.dineflow.mapper.ShoppingCartMapper;
import com.dineflow.service.IShoppingCartService;
import com.dineflow.utils.ThreadLocalUtil;
import com.dineflow.vo.ShoppingCartItemVO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
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
     * 向购物车添加菜品或套餐
     */
    @Override
    public void addItemsToCart(ShoppingCartDTO shoppingCartDTO) {

        Long userId = ThreadLocalUtil.getCurrentId();

        ShoppingCart shoppingCart = BeanUtil.copyProperties(shoppingCartDTO, ShoppingCart.class);

        // 无口味统一存为 null，与后面的 IS NULL 查询保持一致。
        if (StrUtil.isBlank(shoppingCart.getDishFlavor())) {
            shoppingCart.setDishFlavor(null);
        }

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
            // 已存在，数量 +1，同一商品的数量不能超过 100
            // 数量和版本在同一条UPDATE中递增。
            boolean updated = lambdaUpdate()
                    .eq(ShoppingCart::getId, existedCart.getId())
                    .eq(ShoppingCart::getUserId, userId)
                    .ge(ShoppingCart::getNumber, 1)
                    .lt(ShoppingCart::getNumber, 99)
                    .setSql("number = number + 1")
                    .setSql("version = version + 1")
                    .update();
            if (!updated) {
                throw new InvalidParameterException("单项商品最多99份，或购物车已变化，请刷新后重试");
            }
        } else {
            // 不存在，新增购物车记录，还要判断添加的到底是套餐还是菜品，获取图像和价格
            // 新增购物车记录，前提购物车中的商品数不能超过100
            long cartRows = lambdaQuery()
                    .eq(ShoppingCart::getUserId, userId)
                    .count();
            if (cartRows >= 100) {
                throw new InvalidParameterException("购物车最多支持100条商品");
            }

            shoppingCart.setNumber(1);
            shoppingCart.setVersion(0);

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
     * 购物车中的商品数量减一
     */
    @Override
    public void subItemToCart(ShoppingCartDTO dto) {

        Long userId = ThreadLocalUtil.getCurrentId();

        if (userId == null) {
            throw new BaseException(MessageConstant.USER_NOT_LOGIN);
        }

        if (dto == null) {
            throw new InvalidParameterException("购物车参数不能为空");
        }

        boolean hasDish = dto.getDishId() != null;
        boolean hasSetmeal = dto.getSetmealId() != null;

        if (hasDish == hasSetmeal) {
            throw new InvalidParameterException("菜品ID和套餐ID必须且只能填写一个");
        }

        LambdaQueryWrapper<ShoppingCart> wrapper =
                new LambdaQueryWrapper<ShoppingCart>()
                        .eq(ShoppingCart::getUserId, userId)
                        .eq(hasDish, ShoppingCart::getDishId, dto.getDishId())
                        .eq(hasSetmeal, ShoppingCart::getSetmealId, dto.getSetmealId());

        if (hasDish) {
            if (StrUtil.isNotBlank(dto.getDishFlavor())) {
                wrapper.eq(ShoppingCart::getDishFlavor, dto.getDishFlavor());
            } else {
                wrapper.isNull(ShoppingCart::getDishFlavor);
            }
        }

        ShoppingCart existedCart = getOne(wrapper);

        if (existedCart == null) {
            return;
        }

        Integer number = existedCart.getNumber();
        Integer version = existedCart.getVersion();

        if (number == null || number < 1 || version == null || version < 0) {
            throw new InvalidParameterException("购物车数据异常，请清空后重新加购");
        }

        boolean changed;

        if (number > 1) {
            // 只有查询后没有被修改，才能执行本次减一。
            changed = lambdaUpdate()
                    .eq(ShoppingCart::getId, existedCart.getId())
                    .eq(ShoppingCart::getUserId, userId)
                    .eq(ShoppingCart::getVersion, version)
                    .gt(ShoppingCart::getNumber, 1)
                    .setSql("number = number - 1")
                    .setSql("version = version + 1")
                    .update();
        } else {
            // 数量为1时删除记录。
            // 版本和数量条件防止把查询后被加购的记录直接删除。
            changed = lambdaUpdate()
                    .eq(ShoppingCart::getId, existedCart.getId())
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
