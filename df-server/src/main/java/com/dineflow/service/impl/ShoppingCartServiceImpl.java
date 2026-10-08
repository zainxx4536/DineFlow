package com.dineflow.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.dineflow.dto.ShoppingCartDTO;
import com.dineflow.entity.ShoppingCart;
import com.dineflow.mapper.ShoppingCartMapper;
import com.dineflow.order.service.CartMutationService;
import com.dineflow.service.IShoppingCartService;
import com.dineflow.utils.ThreadLocalUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

/** 写操作统一委托，避免不同入口遗漏版本及规格规则。 */
@Service
@RequiredArgsConstructor
public class ShoppingCartServiceImpl extends ServiceImpl<ShoppingCartMapper,ShoppingCart> implements IShoppingCartService {
    private final CartMutationService mutations;
    @Override public void addItemsToCart(ShoppingCartDTO dto) { mutations.add(ThreadLocalUtil.getCurrentId(),dto,1); }
    @Override public void subItemToCart(ShoppingCartDTO dto) { mutations.sub(ThreadLocalUtil.getCurrentId(),dto); }
    @Override public void cleanShoppingCart() { mutations.clear(ThreadLocalUtil.getCurrentId()); }
    @Override public List<ShoppingCart> showShoppingCart() {
        return lambdaQuery().eq(ShoppingCart::getUserId,ThreadLocalUtil.getCurrentId())
                .orderByDesc(ShoppingCart::getCreateTime).orderByDesc(ShoppingCart::getId).list();
    }
}
