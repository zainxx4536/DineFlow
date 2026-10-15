package com.dineflow.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.dineflow.constant.MessageConstant;
import com.dineflow.dto.AddressBookAddDTO;
import com.dineflow.dto.AddressBookUpdateDTO;
import com.dineflow.entity.AddressBook;
import com.dineflow.exception.BaseException;
import com.dineflow.exception.InvalidParameterException;
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
     * 新增地址：客户端仅提供收件内容
     */
    @Override
    public void addAddress(AddressBookAddDTO dto) {
        Long userId = requireAddressUser();

        AddressBook address = buildAddressContent(dto);

        // 受保护字段由后端确定
        address.setId(null);
        address.setUserId(userId);
        address.setIsDefault(0);
        address.setVersion(0);

        if (baseMapper.insert(address) != 1) {
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
     * 编辑整份收件内容，并检查编辑依据的原版本
     */
    @Override
    public void modifyAddressById(AddressBookUpdateDTO dto) {
        Long userId = requireAddressUser();

        if (dto == null || dto.getId() == null || dto.getId() <= 0) {
            throw new InvalidParameterException("地址ID不合法");
        }

        if (dto.getExpectedVersion() == null || dto.getExpectedVersion() < 0 || dto.getExpectedVersion() == Integer.MAX_VALUE) {
            throw new InvalidParameterException("地址版本不合法，请重新加载");
        }

        AddressBook content = buildAddressContent(dto);

        boolean updated = lambdaUpdate()
                // 定位与归属条件
                .eq(AddressBook::getId, dto.getId())
                .eq(AddressBook::getUserId, userId)
                // CAS：必须仍为用户开始编辑时的版本
                .eq(AddressBook::getVersion, dto.getExpectedVersion())
                // 只更新允许修改的收件字段
                .set(AddressBook::getConsignee, content.getConsignee())
                .set(AddressBook::getPhone, content.getPhone())
                .set(AddressBook::getSex, content.getSex())
                .set(AddressBook::getProvinceCode, content.getProvinceCode())
                .set(AddressBook::getProvinceName, content.getProvinceName())
                .set(AddressBook::getCityCode, content.getCityCode())
                .set(AddressBook::getCityName, content.getCityName())
                .set(AddressBook::getDistrictCode, content.getDistrictCode())
                .set(AddressBook::getDistrictName, content.getDistrictName())
                .set(AddressBook::getDetail, content.getDetail())
                .set(AddressBook::getLabel, content.getLabel())
                // 新版本由数据库在原值基础上递增
                .setSql("version = version + 1")
                .update();

        if (updated) {
            return;
        }

        // 只用于解释失败原因，不拿新版本自动重试
        boolean owned = lambdaQuery()
                .eq(AddressBook::getId, dto.getId())
                .eq(AddressBook::getUserId, userId)
                .exists();

        if (!owned) {
            throw new BaseException("地址不存在");
        }

        throw new BaseException("地址已变化，请重新加载后再编辑");
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


    /**
     * 获取当前登录用户
     */
    private Long requireAddressUser() {
        Long userId = ThreadLocalUtil.getCurrentId();

        if (userId == null) {
            throw new BaseException(MessageConstant.USER_NOT_LOGIN);
        }

        return userId;
    }

    /**
     * 校验并显式构造可编辑的收件内容
     * 新增、编辑共用。
     * 不复制客户端的主键、归属、默认状态或版本。
     */
    private AddressBook buildAddressContent(AddressBookAddDTO dto) {
        if (dto == null) {
            throw new InvalidParameterException("地址参数不能为空");
        }

        AddressBook address = new AddressBook();

        address.setConsignee(addressText(dto.getConsignee(), 50, "收件人", true));
        address.setPhone(addressText(dto.getPhone(), 11, "手机号", true));
        address.setSex(addressText(dto.getSex(), 2, "性别", false));
        address.setProvinceCode(addressText(dto.getProvinceCode(), 12, "省份编码", false));
        address.setProvinceName(addressText(dto.getProvinceName(), 32, "省份名称", true));
        address.setCityCode(addressText(dto.getCityCode(), 12, "城市编码", false));
        address.setCityName(addressText(dto.getCityName(), 32, "城市名称", true));
        address.setDistrictCode(addressText(dto.getDistrictCode(), 12, "区县编码", false));
        address.setDistrictName(addressText(dto.getDistrictName(), 32, "区县名称", true));
        address.setDetail(addressText(dto.getDetail(), 200, "详细地址", true));
        address.setLabel(addressText(dto.getLabel(), 100, "地址标签", false));
        if (!address.getPhone().matches("^1[3-9]\\d{9}$")) {
            throw new InvalidParameterException("手机号格式不正确");
        }

        if (address.getSex() != null
                && !"0".equals(address.getSex())
                && !"1".equals(address.getSex())) {
            throw new InvalidParameterException("性别只能为0或1");
        }

        return address;
    }

    /**
     * 检查文本必填和长度，并规范化首尾空白
     */
    private String addressText(String value, int maxLength, String fieldName, boolean required) {

        if (value == null || value.isBlank()) {
            if (required) {
                throw new InvalidParameterException(fieldName + "不能为空");
            }

            return null;
        }

        String text = value.strip();

        if (text.length() > maxLength) {
            throw new InvalidParameterException(fieldName + "不能超过" + maxLength + "个字符");
        }

        return text;
    }
}
