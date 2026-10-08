package com.dineflow.order.service;

import static com.dineflow.constant.BusinessErrorCode.USER_NOT_LOGIN;
import static com.dineflow.order.support.SettlementErrors.error;

import com.dineflow.mapper.SettlementLockMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 统一顺序：目录共享锁 -> 用户行 -> 地址/购物车 -> 套餐/菜品。 后台目录写入先取独占锁，覆盖关系新增的幻行窗口。首版采用一个目录保护行。 */
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class SettlementLocks {
    private final SettlementLockMapper mapper;

    // 共享锁：多个结算可以同时读取，后台商品编辑需要等待。
    public void catalogRead() {
        if (mapper.readCatalog() == null) throw new IllegalStateException("缺少 catalog_guard 初始化数据");
    }

    // 独占锁：修改菜品、规格或套餐组成之前取得，直到当前事务提交才释放。
    public void catalogWrite() {
        if (mapper.writeCatalog() == null)
            throw new IllegalStateException("缺少 catalog_guard 初始化数据");
    }

    // 用户行始终存在。首次加购还没有购物车行时，也能把同一用户的操作排队。
    public void cartOwner(Long userId) {
        if (userId == null || mapper.lockUser(userId) == null) throw error(USER_NOT_LOGIN);
    }
}
