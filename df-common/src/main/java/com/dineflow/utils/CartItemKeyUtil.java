package com.dineflow.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.json.JSONUtil;
import com.dineflow.exception.InvalidParameterException;

import java.util.Arrays;

public final class CartItemKeyUtil {

    private CartItemKeyUtil() {
    }

    /**
     * 统一无口味的表示。
     * null、空字符串、纯空白文本，统一为 null。
     * 有内容的口味保持原样，不拆分、不排序。
     */
    public static String normalizeFlavor(String flavor) {
        if (StrUtil.isBlank(flavor)) {
            return null;
        }

        if (flavor.length() > 50) {
            throw new InvalidParameterException("口味内容不能超过50个字符");
        }

        return flavor;
    }

    /**
     * 根据商品类型、商品ID、口味生成同款标识。
     */
    public static String build(Long dishId, Long setmealId, String flavor) {

        // 两个都没有，或者两个都有，均不允许。
        if ((dishId == null) == (setmealId == null)) {
            throw new InvalidParameterException("菜品ID和套餐ID必须且只能填写一个");
        }

        Long productId = dishId != null ? dishId : setmealId;

        if (productId <= 0) {
            throw new InvalidParameterException("商品ID必须大于0");
        }

        String normalizedFlavor = normalizeFlavor(flavor);

        if (setmealId != null && normalizedFlavor != null) {
            throw new InvalidParameterException("套餐不能填写菜品口味");
        }

        /*
         * 示例：
         * ["D",69,"微辣"]
         * ["D",69,"中辣"]
         * ["S",69,""]
         *
         * D 表示菜品，S 表示套餐。
         * 使用固定的数组结构，避免直接拼接字符串产生歧义。
         */
        String source = JSONUtil.toJsonStr(Arrays.asList(
                dishId != null ? "D" : "S",
                productId,
                normalizedFlavor == null ? "" : normalizedFlavor
        ));

        return SecureUtil.sha256(source);
    }
}