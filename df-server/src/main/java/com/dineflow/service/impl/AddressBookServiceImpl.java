package com.dineflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.dineflow.constant.MessageConstant;
import com.dineflow.entity.AddressBook;
import com.dineflow.exception.AddressBookBusinessException;
import com.dineflow.exception.BaseException;
import com.dineflow.mapper.AddressBookMapper;
import com.dineflow.service.IAddressBookService;
import com.dineflow.utils.ThreadLocalUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * <p>
 * 地址簿 服务实现类
 * </p>
 *
 * @author zainxx
 * @since 2026-09-08
 */
@Service
public class AddressBookServiceImpl extends ServiceImpl<AddressBookMapper, AddressBook> implements IAddressBookService {

    /**
     * 查询当前登录用户的所有地址信息
     */
    @Override
    public List<AddressBook> getAddressInfo() {
        Long userId = ThreadLocalUtil.getCurrentId();
        return lambdaQuery().eq(AddressBook::getUserId, userId).list();
    }

    /**
     * 新增地址
     */
    @Override
    public void addAddress(AddressBook addressBook) {
        Long userId = ThreadLocalUtil.getCurrentId();
        addressBook.setUserId(userId);
        addressBook.setIsDefault(0);
        addressBook.setVersion(1L);
        save(addressBook);
    }

    /**
     * 根据ID查询地址
     */
    @Override
    public AddressBook getAddressById(Long id) {
        Long userId = ThreadLocalUtil.getCurrentId();
        return lambdaQuery()
                .eq(AddressBook::getUserId, userId)
                .eq(AddressBook::getId, id)
                .one();
    }

    /**
     * 根据ID修改地址
     */
    @Override
    public void modifyAddressById(AddressBook addressBook) {
        Long userId = ThreadLocalUtil.getCurrentId();
        LambdaUpdateWrapper<AddressBook> wrapper =
                new LambdaUpdateWrapper<AddressBook>()
                        .eq(AddressBook::getUserId, userId)
                        .eq(AddressBook::getId, addressBook.getId())
                        .eq(AddressBook::getVersion, addressBook.getVersion())
                        .setSql("version = version + 1");

        boolean success = update(addressBook, wrapper);

        if (!success) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_CHANGED);
        }
    }

    /**
     * 设置默认地址，设置默认地址只改变：is_default，因此不递增 version
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void setDefaultAddress(AddressBook addressBook) {

        Long userId = ThreadLocalUtil.getCurrentId();

        boolean exists = lambdaQuery()
                .eq(AddressBook::getUserId, userId)
                .eq(AddressBook::getId, addressBook.getId())
                .exists();

        if (!exists) {
            throw new BaseException("地址不存在");
        }

        // 所有地址取消默认
        lambdaUpdate()
                .eq(AddressBook::getUserId, userId)
                .set(AddressBook::getIsDefault, 0)
                .update();

        // 当前地址设为默认
        lambdaUpdate()
                .eq(AddressBook::getUserId, userId)
                .eq(AddressBook::getId, addressBook.getId())
                .set(AddressBook::getIsDefault, 1)
                .update();
    }

    /**
     * 根据ID删除地址
     */
    @Override
    public void deleteAddressById(Long id) {
        Long userId = ThreadLocalUtil.getCurrentId();

        lambdaUpdate()
                .eq(AddressBook::getUserId, userId)
                .eq(AddressBook::getId, id)
                .remove();
    }

    /**
     * 查询默认地址
     */
    @Override
    public AddressBook getDefaultAddress() {
        Long userId = ThreadLocalUtil.getCurrentId();
        return lambdaQuery().eq(AddressBook::getUserId, userId).eq(AddressBook::getIsDefault, 1).one();
    }
}
