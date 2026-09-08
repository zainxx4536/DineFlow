package com.dineflow.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.dineflow.entity.ShoppingCart;
import com.dineflow.mapper.ShoppingCartMapper;
import com.dineflow.service.IShoppingCartService;
import org.springframework.stereotype.Service;

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

}
