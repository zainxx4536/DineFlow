package com.dineflow.mapper;

import com.dineflow.entity.ShoppingCart;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 * 购物车 Mapper 接口
 * </p>
 *
 * @author zainxx
 * @since 2026-09-08
 */
public interface ShoppingCartMapper extends BaseMapper<ShoppingCart> {
    /**
     * 在订单事务中锁定本人整车。
     */
    List<ShoppingCart> lockOwnedCart(
            @Param("currentUserId") Long currentUserId);

    /**
     * 精确消费本次成交的购物车条目。
     */
    int deleteOwnedCartAtVersion(
            @Param("cartId") Long cartId,
            @Param("currentUserId") Long currentUserId,
            @Param("version") Integer version,
            @Param("number") Integer number);
}
