package com.dineflow.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.dineflow.entity.AddressBook;

import java.util.List;

/**
 * <p>
 * 地址簿 服务类
 * </p>
 *
 * @author zainxx
 * @since 2026-09-08
 */
public interface IAddressBookService extends IService<AddressBook> {

    List<AddressBook> getAddressInfo();

    void addAddress(AddressBook addressBook);

    AddressBook getAddressById(Long id);

    void modifyAddressById(AddressBook addressBook);

    void setDefaultAddress(AddressBook addressBook);

    void deleteAddressById(Long id);

    AddressBook getDefaultAddress();
}
