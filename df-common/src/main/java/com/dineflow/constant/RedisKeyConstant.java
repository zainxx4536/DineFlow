package com.dineflow.constant;

/**
 * redis 中存储的 key 名
 */
public class RedisKeyConstant {
    //分类缓存，dineflow:category:list::all/typeId
    public static final String CATEGORY_LIST = "dineflow:category:list";
    //分类中套餐缓存，dineflow:category:setmeal::分类ID
    public static final String CATEGORY_SETMEAL = "dineflow:category:setmeal";
    //分类中菜品及口味缓存，dineflow:category:dish:flavor::分类ID
    public static final String CATEGORY_DISH_FLAVOR = "dineflow:category:dish:flavor";
    //套餐中菜品缓存，dineflow:setmeal:dish::套餐ID
    public static final String SETMEAL_DISH = "dineflow:setmeal:dish";
    //店铺状态
    public static final String SHOP_STATUS = "dineflow:shop:status";
}
