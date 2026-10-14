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
    /**
     * 在订单事务中锁定本人收货地址。
     */
    AddressBook lockOwnedAddress(
            @Param("addressBookId") Long addressBookId,
            @Param("currentUserId") Long currentUserId);
}
