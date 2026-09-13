package com.dineflow.controller.admin;

import cn.hutool.core.util.StrUtil;
import com.dineflow.constant.RedisKeyConstant;
import com.dineflow.exception.BaseException;
import com.dineflow.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/shop")
@Slf4j
@Api(tags = "店铺操作接口")
@RequiredArgsConstructor
public class ShopController {

    private final StringRedisTemplate redisTemplate;

    /**
     * 设置店铺营业状态
     */
    @PutMapping("/{status}")
    @ApiOperation("设置店铺营业状态")
    public Result<String> setShopStatus(@PathVariable Integer status) {
        log.info("设置店铺的营业状态为：{}", status == 1 ? "营业中" : "打烊中");
        redisTemplate.opsForValue().set(RedisKeyConstant.SHOP_STATUS, status.toString());
        return Result.success();
    }

    /**
     * 获取店铺营业状态
     */
    @GetMapping("/status")
    @ApiOperation("获取店铺营业状态")
    public Result<Integer> getShopStatus() {
        log.info("管理者获取店铺的营业状态");
        String status = redisTemplate.opsForValue().get(RedisKeyConstant.SHOP_STATUS);
        if (StrUtil.isBlank(status)) {
            throw new BaseException("店铺状态异常");
        }
        return Result.success(Integer.valueOf(status));
    }
}
