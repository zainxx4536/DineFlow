package com.dineflow.order.service;

import static com.dineflow.constant.BusinessErrorCode.*;
import static com.dineflow.order.support.SettlementErrors.error;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dineflow.dto.ShoppingCartDTO;
import com.dineflow.entity.ShoppingCart;
import com.dineflow.mapper.ShoppingCartMapper;
import com.dineflow.order.model.SettlementItem;
import com.dineflow.order.support.FlavorSelection;
import com.dineflow.order.support.SettlementValidation;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 普通加购、CAS 修改及再来一单共用唯一入口；
 * 用户行锁解决首次插入无现成购物车行可锁的问题。
 */
@Service
@RequiredArgsConstructor
public class CartMutationService {
    private final ShoppingCartMapper mapper;
    private final SettlementLocks locks;
    private final OrderSettlementService settlement;
    private final FlavorSelection flavors;

    @Transactional(rollbackFor = Exception.class)
    public void add(Long userId, ShoppingCartDTO dto, int quantity) {
        identity(dto);
        SettlementValidation.quantity(quantity);
        locks.catalogRead();
        locks.cartOwner(userId);
        ShoppingCart candidate =
                ShoppingCart.builder()
                        .userId(userId)
                        .dishId(dto.getDishId())
                        .setmealId(dto.getSetmealId())
                        .dishFlavor(flavors.normalize(dto.getDishFlavor()))
                        .number(quantity)
                        .version(1L)
                        .build();
        List<ShoppingCart> rows =
                mapper.selectList(
                        new LambdaQueryWrapper<ShoppingCart>()
                                .eq(ShoppingCart::getUserId, userId)
                                .orderByAsc(ShoppingCart::getId)
                                .last("FOR UPDATE"));
        ShoppingCart target = null;
        if (dto.getId() != null) {
            // 带 ID/version：修改用户看到的那一行，不能误改另一个商品或规格。
            for (ShoppingCart row : rows) {
                if (Objects.equals(row.getId(), dto.getId())) {
                    target = row;
                    break;
                }
            }
            if (target == null
                    || !Objects.equals(target.getVersion(), dto.getVersion())
                    || !same(target, candidate)) {
                throw error(CART_CHANGED);
            }
        } else {
            // 不带 ID/version：在锁保护下追加数量，已有同规格就累加，否则新增。
            for (ShoppingCart row : rows) {
                if (same(row, candidate)) {
                    if (target != null) {
                        throw error(CART_CHANGED);
                    }
                    target = row;
                }
            }
        }
        SettlementItem item = settlement.buildLockedSettlementItems(List.of(candidate)).get(0);
        if (target == null) {
            // 新增不复制请求主键；价格、名称、图片均取本次服务端校验结果。
            candidate.setName(item.getName());
            candidate.setImage(item.getImage());
            candidate.setAmount(item.getUnitPrice());
            candidate.setDishFlavor(item.getDishFlavor());
            candidate.setCreateTime(LocalDateTime.now());
            try {
                if (mapper.insert(candidate) != 1) throw error(CART_CHANGED);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                throw error(CART_CHANGED);
            }
        } else {
            SettlementValidation.quantity(target.getNumber());
            if (target.getNumber() > SettlementValidation.MAX_QUANTITY - quantity)
                throw error(INVALID_PARAMETER);
            int affectedRows =
                    mapper.update(
                            null,
                            new LambdaUpdateWrapper<ShoppingCart>()
                                    .eq(ShoppingCart::getId, target.getId())
                                    .eq(ShoppingCart::getUserId, userId)
                                    .eq(ShoppingCart::getVersion, target.getVersion())
                                    .set(ShoppingCart::getNumber, target.getNumber() + quantity)
                                    .set(ShoppingCart::getDishFlavor, item.getDishFlavor())
                                    .set(ShoppingCart::getName, item.getName())
                                    .set(ShoppingCart::getImage, item.getImage())
                                    .set(ShoppingCart::getAmount, item.getUnitPrice())
                                    .setSql("version = version + 1"));
            if (affectedRows != 1) throw error(CART_CHANGED);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void sub(Long userId, ShoppingCartDTO dto) {
        identity(dto);
        if (dto.getId() == null) throw error(INVALID_PARAMETER);
        locks.cartOwner(userId);
        List<ShoppingCart> rows = mapper.selectForUpdate(userId, List.of(dto.getId()));
        if (rows.size() != 1) throw error(CART_CHANGED);
        ShoppingCart row = rows.get(0);
        ShoppingCart requested =
                ShoppingCart.builder()
                        .dishId(dto.getDishId())
                        .setmealId(dto.getSetmealId())
                        .dishFlavor(dto.getDishFlavor())
                        .build();
        if (!Objects.equals(row.getVersion(), dto.getVersion()) || !same(row, requested))
            throw error(CART_CHANGED);
        SettlementValidation.quantity(row.getNumber());
        int affectedRows;
        if (row.getNumber() == 1) {
            affectedRows =
                    mapper.delete(
                            new LambdaQueryWrapper<ShoppingCart>()
                                    .eq(ShoppingCart::getId, row.getId())
                                    .eq(ShoppingCart::getUserId, userId)
                                    .eq(ShoppingCart::getVersion, dto.getVersion())
                                    .eq(ShoppingCart::getNumber, 1));
        } else {
            affectedRows =
                    mapper.update(
                            null,
                            new LambdaUpdateWrapper<ShoppingCart>()
                                    .eq(ShoppingCart::getId, row.getId())
                                    .eq(ShoppingCart::getUserId, userId)
                                    .eq(ShoppingCart::getVersion, dto.getVersion())
                                    .gt(ShoppingCart::getNumber, 1)
                                    .setSql("number = number - 1")
                                    .setSql("version = version + 1"));
        }
        if (affectedRows != 1) {
            throw error(CART_CHANGED);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void clear(Long userId) {
        locks.cartOwner(userId);
        LambdaQueryWrapper<ShoppingCart> query =
                new LambdaQueryWrapper<ShoppingCart>().eq(ShoppingCart::getUserId, userId);
        int size = mapper.selectList(query.last("FOR UPDATE")).size();
        if (mapper.delete(
                        new LambdaQueryWrapper<ShoppingCart>().eq(ShoppingCart::getUserId, userId))
                != size) throw error(CART_CHANGED);
    }

    private void identity(ShoppingCartDTO dto) {
        if (dto == null
                || (dto.getDishId() == null) == (dto.getSetmealId() == null)
                || (dto.getDishId() != null && !SettlementValidation.positive(dto.getDishId()))
                || (dto.getSetmealId() != null
                        && !SettlementValidation.positive(dto.getSetmealId()))
                || (dto.getId() == null) != (dto.getVersion() == null)
                || (dto.getId() != null
                        && (!SettlementValidation.positive(dto.getId())
                                || !SettlementValidation.positive(dto.getVersion()))))
            throw error(INVALID_PARAMETER);
    }

    private boolean same(ShoppingCart a, ShoppingCart b) {
        return Objects.equals(a.getDishId(), b.getDishId())
                && Objects.equals(a.getSetmealId(), b.getSetmealId())
                && flavors.normalize(a.getDishFlavor())
                        .equals(flavors.normalize(b.getDishFlavor()));
    }
}
