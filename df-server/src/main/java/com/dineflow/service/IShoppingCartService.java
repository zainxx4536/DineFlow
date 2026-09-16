package com.dineflow.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.dineflow.dto.ShoppingCartDTO;
import com.dineflow.entity.ShoppingCart;

import java.util.List;

/**
 * <p>
 * 购物车 服务类
 * </p>
 *
 * @author zainxx
 * @since 2026-09-08
 */
public interface IShoppingCartService extends IService<ShoppingCart> {

    void addItemsToCart(ShoppingCartDTO shoppingCartDTO);

    List<ShoppingCart> showShoppingCart();

    void subItemToCart(ShoppingCartDTO shoppingCartDTO);

    void cleanShoppingCart();
}
