package com.dineflow.mapper;

import com.dineflow.entity.AddressBook;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 * 地址簿 Mapper 接口
 * </p>
 *
 * @author zainxx
 * @since 2026-09-08
 */
public interface AddressBookMapper extends BaseMapper<AddressBook> {
    AddressBook selectForUpdate(@Param("userId") Long userId, @Param("addressBookId") Long addressBookId);
}
