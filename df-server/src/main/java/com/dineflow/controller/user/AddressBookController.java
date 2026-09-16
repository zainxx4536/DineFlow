package com.dineflow.controller.user;


import com.dineflow.entity.AddressBook;
import com.dineflow.result.Result;
import com.dineflow.service.IAddressBookService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 地址簿 前端控制器
 * </p>
 *
 * @author zainxx
 * @since 2026-09-08
 */
@RestController
@RequestMapping("/user/addressBook")
@Api(tags = "C端-地址簿接口")
@Slf4j
@RequiredArgsConstructor
public class AddressBookController {

    private final IAddressBookService addressBookService;

    /**
     * 查询当前登录用户的所有地址信息
     */
    @GetMapping("/list")
    @ApiOperation("查询当前登录用户的所有地址信息")
    public Result<List<AddressBook>> getAddressInfo() {
        log.info("查询当前登录用户的所有地址信息");
        List<AddressBook> addressBookList = addressBookService.getAddressInfo();
        return Result.success(addressBookList);
    }

    /**
     * 新增地址
     */
    @PostMapping
    @ApiOperation("新增地址")
    public Result<String> addAddress(@RequestBody AddressBook addressBook) {
        log.info("新增地址：{}", addressBook);
        addressBookService.addAddress(addressBook);
        return Result.success();
    }

    /**
     * 根据ID查询地址
     */
    @GetMapping("/{id}")
    @ApiOperation("根据ID查询地址")
    public Result<AddressBook> getAddressById(@PathVariable Long id) {
        log.info("根据ID查询地址: {}", id);
        AddressBook addressBook = addressBookService.getAddressById(id);
        return Result.success(addressBook);
    }

    /**
     * 根据ID修改地址
     */
    @PutMapping
    @ApiOperation("根据id修改地址")
    public Result<String> modifyAddressById(@RequestBody AddressBook addressBook) {
        log.info("根据ID修改地址: {}", addressBook);
        addressBookService.modifyAddressById(addressBook);
        return Result.success();
    }

    /**
     * 设置默认地址
     */
    @PutMapping("/default")
    @ApiOperation("设置默认地址")
    public Result<String> setDefaultAddress(@RequestBody AddressBook addressBook) {
        log.info("设置默认地址：{}", addressBook);
        addressBookService.setDefaultAddress(addressBook);
        return Result.success();
    }

    /**
     * 根据ID删除地址
     */
    @DeleteMapping
    @ApiOperation("根据ID删除地址")
    public Result<String> deleteAddressById(Long id) {
        log.info("根据ID删除地址：{}", id);
        addressBookService.deleteAddressById(id);
        return Result.success();
    }

    /**
     * 查询默认地址
     */
    @GetMapping("default")
    @ApiOperation("查询默认地址")
    public Result<AddressBook> getDefaultAddress() {
        log.info("查询默认地址");
        AddressBook defaultAddress = addressBookService.getDefaultAddress();
        if (defaultAddress != null) {
            return Result.success(defaultAddress);
        }
        return Result.error("没有查询到默认地址");
    }
}
