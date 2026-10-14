package com.dineflow.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.dineflow.constant.MessageConstant;
import com.dineflow.entity.AddressBook;
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

        if (userId == null) {
            throw new BaseException(MessageConstant.USER_NOT_LOGIN);
        }

        if (addressBook == null) {
            throw new BaseException("地址参数不能为空");
        }

        // 由后端确定，忽略客户端传入的值。
        addressBook.setId(null);
        addressBook.setUserId(userId);
        addressBook.setIsDefault(0);
        addressBook.setVersion(0);

        if (!save(addressBook)) {
            throw new BaseException("地址新增失败");
        }
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

        if (userId == null) {
            throw new BaseException(MessageConstant.USER_NOT_LOGIN);
        }

        if (addressBook == null
                || addressBook.getId() == null
                || addressBook.getId() <= 0) {
            throw new BaseException("地址ID不合法");
        }

        // 当前前端提交完整地址表单。
        // 显式更新可编辑字段，避免客户端覆盖归属、默认状态或版本。
        boolean updated = lambdaUpdate()
                .eq(AddressBook::getId, addressBook.getId())
                .eq(AddressBook::getUserId, userId)
                .set(AddressBook::getConsignee, addressBook.getConsignee())
                .set(AddressBook::getPhone, addressBook.getPhone())
                .set(AddressBook::getSex, addressBook.getSex())
                .set(AddressBook::getProvinceCode, addressBook.getProvinceCode())
                .set(AddressBook::getProvinceName, addressBook.getProvinceName())
                .set(AddressBook::getCityCode, addressBook.getCityCode())
                .set(AddressBook::getCityName, addressBook.getCityName())
                .set(AddressBook::getDistrictCode, addressBook.getDistrictCode())
                .set(AddressBook::getDistrictName, addressBook.getDistrictName())
                .set(AddressBook::getDetail, addressBook.getDetail())
                .set(AddressBook::getLabel, addressBook.getLabel())
                .setSql("version = version + 1")
                .update();

        if (!updated) {
            throw new BaseException("地址不存在或不属于当前用户");
        }
    }

    /**
     * 设置默认地址
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
