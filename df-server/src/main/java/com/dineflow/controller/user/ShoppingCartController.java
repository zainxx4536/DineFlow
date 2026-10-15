package com.dineflow.controller.user;


import com.dineflow.dto.ShoppingCartDTO;
import com.dineflow.dto.ShoppingCartSubDTO;
import com.dineflow.result.Result;
import com.dineflow.service.IShoppingCartService;
import com.dineflow.vo.ShoppingCartItemVO;
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
    public Result<List<ShoppingCartItemVO>> showShoppingCart() {
        log.info("查看购物车");
        List<ShoppingCartItemVO> shoppingCarts = shoppingCartService.showShoppingCart();
        return Result.success(shoppingCarts);
    }

    /**
     * 购物车指定条目数量减一。
     */
    @PostMapping("/sub")
    @ApiOperation("购物车指定条目数量减一")
    public Result<String> subItemToCart(@RequestBody ShoppingCartSubDTO dto) {
        shoppingCartService.subItemToCart(dto);
        return Result.success();
    }

    /**
     * 移除购物车中的整条记录。
     */
    @DeleteMapping("/{cartId}")
    @ApiOperation("移除购物车指定条目")
    public Result<String> removeItemFromCart(
            @PathVariable("cartId") Long cartId) {

        shoppingCartService.removeItemFromCart(cartId);
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
