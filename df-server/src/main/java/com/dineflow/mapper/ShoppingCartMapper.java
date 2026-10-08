package com.dineflow.mapper;

import com.dineflow.dto.SelectedCartItemDTO;
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
     * 锁定本次选中的购物车条目
     */
    List<ShoppingCart> selectForUpdate(@Param("userId") Long userId, @Param("cartItemIds") List<Long> cartItemIds);

    /**
     * 精确删除本次已经消费的购物车条目
     */
    int deleteSelectedItems(@Param("userId") Long userId, @Param("items") List<SelectedCartItemDTO> items
    );
}
