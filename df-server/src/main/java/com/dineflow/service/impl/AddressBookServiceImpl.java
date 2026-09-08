package com.dineflow.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.dineflow.entity.AddressBook;
import com.dineflow.mapper.AddressBookMapper;
import com.dineflow.service.IAddressBookService;
import org.springframework.stereotype.Service;

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

}
