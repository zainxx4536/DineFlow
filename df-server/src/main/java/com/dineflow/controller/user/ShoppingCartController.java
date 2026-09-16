package com.dineflow.controller.user;


import com.dineflow.dto.ShoppingCartDTO;
import com.dineflow.entity.ShoppingCart;
import com.dineflow.result.Result;
import com.dineflow.service.IShoppingCartService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 购物车 前端控制器
 * </p>
 *
 * @author zainxx
 * @since 2026-09-08
 */
@RestController
@RequestMapping("/user/shoppingCart")
@Slf4j
@RequiredArgsConstructor
@Api(tags = "C端-购物车接口")
public class ShoppingCartController {

    private final IShoppingCartService shoppingCartService;

    /**
     * 向购物车添加菜品或套餐
     */
    @PostMapping("/add")
    @ApiOperation("向购物车添加菜品或套餐")
    public Result<String> addItemsToCart(@RequestBody ShoppingCartDTO shoppingCartDTO) {
         log.info("向购物车添加菜品或套餐：{}", shoppingCartDTO);
         shoppingCartService.addItemsToCart(shoppingCartDTO);
         return Result.success();
    }

    /**
     * 查看购物车
     */
    @GetMapping("/list")
    @ApiOperation("查看购物车")
    public Result<List<ShoppingCart>> showShoppingCart() {
        log.info("查看购物车");
        List<ShoppingCart> shoppingCarts = shoppingCartService.showShoppingCart();
        return Result.success(shoppingCarts);
    }

    /**
     * 删除购物车中的一个商品
     */
    @PostMapping("/sub")
    @ApiOperation("删除购物车中的一个商品")
    public Result<String> subItemToCart(@RequestBody ShoppingCartDTO shoppingCartDTO){
        log.info("删除购物车中的一个商品：{}", shoppingCartDTO);
        shoppingCartService.subItemToCart(shoppingCartDTO);
        return Result.success();
    }

    /**
     * 一键清空购物车
     */
    @DeleteMapping("/clean")
    @ApiOperation("一键清空购物车")
    public Result<String> cleanShoppingCart() {
        log.info("一键清空购物车");
        shoppingCartService.cleanShoppingCart();
        return Result.success();
    }
}
