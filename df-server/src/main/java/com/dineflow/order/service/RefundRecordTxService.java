package com.dineflow.order.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dineflow.entity.Orders;
import com.dineflow.entity.PaymentRecord;
import com.dineflow.mapper.PaymentRecordMapper;
import com.dineflow.entity.RefundRecord;
import com.dineflow.exception.OrderBusinessException;
import com.dineflow.mapper.OrdersMapper;
import com.dineflow.mapper.RefundRecordMapper;
import com.dineflow.vo.OrderDetailVO;
import com.wechat.pay.java.service.refund.model.Refund;
import com.wechat.pay.java.service.refund.model.Status;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class RefundRecordTxService {

    private final OrdersMapper ordersMapper;
    private final RefundRecordMapper refundRecordMapper;
    private final PaymentRecordMapper paymentRecordMapper;

    /**
     * 取消或拒单结果与退款意图一起提交。
     * 退款记录创建失败时，订单状态修改也会回滚。
     * 调用方继续负责原有的身份、归属和取消权限检查。
     * rejection=true 表示管理员拒单。
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancelAndPrepare(
            Orders expected,
            String reason,
            boolean rejection) {

        if (expected == null || !Orders.FUNDS_WECHAT.equals(expected.getFundsSource())) {
            throw new OrderBusinessException("历史订单仅供查询，资金操作需人工核验");
        }
        boolean paid = Orders.PAID.equals(expected.getPayStatus());

        boolean allowed = rejection
                ? Orders.TO_BE_CONFIRMED.equals(expected.getStatus()) && paid
                : (Orders.PENDING_PAYMENT.equals(expected.getStatus())
                && Orders.UN_PAID.equals(expected.getPayStatus()))
                || (paid
                && List.of(
                Orders.TO_BE_CONFIRMED,
                Orders.CONFIRMED
        ).contains(expected.getStatus()));

        if (!allowed) {
            throw new OrderBusinessException("当前订单不可取消或拒单");
        }

        Orders change = new Orders();
        change.setStatus(Orders.CANCELLED);
        change.setPayStatus(
                paid ? Orders.REFUNDING : Orders.UN_PAID
        );
        change.setCancelTime(LocalDateTime.now().withNano(0));

        if (rejection) {
            change.setRejectionReason(reason);
        } else {
            change.setCancelReason(reason);
        }

        // 条件更新同时检查查询时的状态，成功后持有订单写锁。
        int changed = ordersMapper.update(
                change,
                Wrappers.<Orders>lambdaUpdate()
                        .eq(Orders::getId, expected.getId())
                        .eq(Orders::getFundsSource, Orders.FUNDS_WECHAT)
                        .eq(Orders::getUserId, expected.getUserId())
                        .eq(Orders::getStatus, expected.getStatus())
                        .eq(Orders::getPayStatus, expected.getPayStatus())
        );

        if (changed != 1) {
            throw new OrderBusinessException("订单状态已变化，请刷新后操作");
        }

        // 未支付取消不创建退款记录。
        if (paid) {
            Orders current = ordersMapper.selectByNumberForUpdate(
                    expected.getNumber()
            );

            checkOrder(current);
            ensureRecord(current);
        }
    }

    /**
     * 准备退款记录。
     * 默认传播行为是 REQUIRED：
     * 1. 在 paySuccess() 内调用时，加入已有支付事务；
     * 2. 在恢复流程调用时，开启并完成自己的短事务。
     */
    @Transactional(rollbackFor = Exception.class)
    public void prepare(String orderNumber) {
        pendingRecordForUpdate(orderNumber);
    }

    /**
     * 统一处理可信退款结果。
     * 只能接收经过 SDK 验签的通知、查询或申请响应。
     * 不为这个方法新增接收客户端退款结果的 HTTP 接口。
     */
    @Transactional(rollbackFor = Exception.class)
    public void applyResult(Refund result) {

        if (result == null
                || StrUtil.isBlank(result.getOutTradeNo())
                || StrUtil.isBlank(result.getOutRefundNo())
                || StrUtil.isBlank(result.getTransactionId())
                || StrUtil.isBlank(result.getRefundId())
                || result.getRefundId().length() > 64
                || result.getAmount() == null
                || result.getStatus() == null) {

            throw new OrderBusinessException("退款结果参数不完整");
        }

        // 先锁订单，再检查、修改退款记录，保持锁顺序一致。
        Orders order = ordersMapper.selectByNumberForUpdate(
                result.getOutTradeNo()
        );

        checkOrder(order);

        long amount = toFen(order.getAmount());

        if (!Objects.equals(
                "RF" + order.getNumber(),
                result.getOutRefundNo()
        )
                || !Objects.equals(
                order.getTransactionId(),
                result.getTransactionId()
        )
                || !"CNY".equals(result.getAmount().getCurrency())
                || !Objects.equals(amount, result.getAmount().getTotal())
                || !Objects.equals(amount, result.getAmount().getRefund())) {

            throw new OrderBusinessException("退款结果与订单不一致");
        }

        RefundRecord record = findRecord(order);

        if (record != null
                && StrUtil.isNotBlank(record.getRefundId())
                && !Objects.equals(
                record.getRefundId(),
                result.getRefundId()
        )) {

            throw new OrderBusinessException("微信退款单号不一致");
        }

        // 已成功的本地结果，不能被旧的处理中或失败结果覆盖。
        if (result.getStatus() != Status.SUCCESS
                && (Orders.REFUND.equals(order.getPayStatus())
                || (record != null
                && RefundRecord.SUCCESS.equals(record.getStatus())))) {

            return;
        }

        if (record == null) {
            record = ensureRecord(order);
        }

        LocalDateTime now = LocalDateTime.now().withNano(0);

        RefundRecord change = new RefundRecord();
        change.setId(record.getId());
        change.setRefundId(result.getRefundId());
        change.setUpdateTime(now);
        LocalDateTime nextRetryTime = null;
        change.setLastError("");

        if (result.getStatus() == Status.SUCCESS) {

            LocalDateTime successAt;

            try {
                successAt = OffsetDateTime
                        .parse(result.getSuccessTime())
                        .atZoneSameInstant(ZoneId.of("Asia/Shanghai"))
                        .toLocalDateTime()
                        .withNano(0);
            } catch (RuntimeException e) {
                throw new OrderBusinessException("退款成功时间无效");
            }

            if (Orders.REFUNDING.equals(order.getPayStatus())) {

                Orders orderChange = new Orders();
                orderChange.setPayStatus(Orders.REFUND);

                int changed = ordersMapper.update(
                        orderChange,
                        Wrappers.<Orders>lambdaUpdate()
                                .eq(Orders::getId, order.getId())
                                .eq(Orders::getStatus, Orders.CANCELLED)
                                .eq(Orders::getPayStatus, Orders.REFUNDING)
                );

                if (changed != 1) {
                    throw new OrderBusinessException("订单退款状态更新失败");
                }
            }

            // 重复成功结果不覆盖第一次保存的退款成功时间。
            if (RefundRecord.SUCCESS.equals(record.getStatus())) {
                return;
            }

            change.setStatus(RefundRecord.SUCCESS);
            change.setSuccessTime(successAt);

        } else if (result.getStatus() == Status.PROCESSING) {

            // 转人工后，不因旧的处理中通知恢复自动申请。
            if (RefundRecord.MANUAL.equals(record.getStatus())) {
                return;
            }

            if (record.getQueryCount() >= RecoveryPolicy.MAX_QUERIES) {
                change.setStatus(RefundRecord.MANUAL);
                change.setLastError("QUERY_LIMIT");
            } else {
                change.setStatus(RefundRecord.PROCESSING);
                nextRetryTime = record.getNextRetryTime();
                if (nextRetryTime == null || !nextRetryTime.isAfter(now)) {
                    nextRetryTime = RecoveryPolicy.nextTime(now, Math.max(1, record.getQueryCount()));
                }
            }

        } else {

            // CLOSED、ABNORMAL 不等于退款成功，也不自动换号退款。
            change.setStatus(RefundRecord.MANUAL);
            change.setLastError(
                    "WECHAT_" + result.getStatus().name()
            );
        }

        if (refundRecordMapper.update(change, Wrappers.<RefundRecord>lambdaUpdate()
                .eq(RefundRecord::getId, record.getId())
                .set(RefundRecord::getNextRetryTime, nextRetryTime)) != 1) {
            throw new OrderBusinessException("退款记录更新失败");
        }
    }

    /**
     * 锁定订单后记录脱敏错误，不重置已保存的退避时间。
     * 不能覆盖已经成功或转人工处理的记录。
     */
    @Transactional(rollbackFor = Exception.class)
    public void rememberError(String orderNumber, String error) {
        RefundRecord record = pendingRecordForUpdate(orderNumber);
        if (record == null || RefundRecord.SUCCESS.equals(record.getStatus())
                || RefundRecord.MANUAL.equals(record.getStatus())) return;
        if (record.getQueryCount() >= RecoveryPolicy.MAX_QUERIES) {
            manual(record, error);
            return;
        }
        refundRecordMapper.update(null, Wrappers.<RefundRecord>lambdaUpdate()
                .eq(RefundRecord::getId, record.getId())
                .in(RefundRecord::getStatus, RefundRecord.PENDING, RefundRecord.PROCESSING)
                .set(RefundRecord::getLastError, error)
                .set(RefundRecord::getUpdateTime, LocalDateTime.now().withNano(0)));
    }

    /**
     * 每批最多取50条到期记录。
     * 已成功、人工处理记录不进入自动恢复。
     */
    public List<RefundRecord> pendingRecords() {

        return refundRecordMapper.selectList(
                Wrappers.<RefundRecord>lambdaQuery()
                        .inSql(RefundRecord::getOrderId, "SELECT id FROM orders WHERE funds_source='WECHAT'")
                        .in(
                                RefundRecord::getStatus,
                                RefundRecord.PENDING,
                                RefundRecord.PROCESSING
                        )
                        .le(
                                RefundRecord::getNextRetryTime,
                                LocalDateTime.now()
                        )
                        .orderByAsc(
                                RefundRecord::getNextRetryTime,
                                RefundRecord::getId
                        )
                        .last("LIMIT 50")
        );
    }

    /**
     * 调用方先完成订单归属或管理员权限检查，再补充只读退款信息。
     * 仅返回公开提示，不将 lastError 原文暴露给客户端。
     */
    public void fillRefundInfo(OrderDetailVO order) {
        if (!Orders.FUNDS_WECHAT.equals(order.getFundsSource())) return;

        RefundRecord record = findRecord(order);

        if (record == null) {
            return;
        }

        order.setRefundStatus(record.getStatus());
        order.setRefundMessage(switch (record.getStatus()) {
            case 0 -> "退款申请待确认，系统将继续查询";
            case 1 -> "退款处理中，请稍后刷新";
            case 2 -> "已退款";
            case 3 -> "退款异常，待人工核查，请联系商家";
            default -> throw new OrderBusinessException("退款记录状态无效");
        });
    }

    /**
     * 读取后核对不可变的订单关联、退款号和金额。
     */
    private RefundRecord findRecord(Orders order) {

        RefundRecord record = refundRecordMapper.selectOne(
                Wrappers.<RefundRecord>lambdaQuery()
                        .eq(RefundRecord::getOrderId, order.getId())
        );

        if (record != null
                && (!Objects.equals(
                record.getOrderNumber(),
                order.getNumber()
        )
                || !Objects.equals(
                record.getRefundNumber(),
                "RF" + order.getNumber()
        )
                || record.getAmount() == null
                || record.getAmount().compareTo(order.getAmount()) != 0
                || record.getStatus() == null
                || !List.of(0, 1, 2, 3).contains(record.getStatus())
                || record.getAttemptCount() == null || record.getAttemptCount() < 0
                || record.getQueryCount() == null || record.getQueryCount() < 0)) {

            throw new OrderBusinessException("退款记录与订单不一致");
        }

        return record;
    }

    /**
     * 调用前已经取得订单锁，同一订单的初始化会串行进行。
     * 已有记录原样复用，不覆盖金额、退款号或成功结果。
     */
    private RefundRecord ensureRecord(Orders order) {

        RefundRecord record = findRecord(order);

        if (record != null) {
            return record;
        }

        LocalDateTime now = LocalDateTime.now().withNano(0);

        record = new RefundRecord();
        record.setOrderId(order.getId());
        record.setOrderNumber(order.getNumber());
        record.setRefundNumber("RF" + order.getNumber());
        record.setAmount(order.getAmount());
        record.setStatus(RefundRecord.PENDING);
        record.setAttemptCount(0);
        record.setQueryCount(0);
        record.setNextRetryTime(now);
        record.setCreateTime(now);
        record.setUpdateTime(now);

        if (refundRecordMapper.insert(record) != 1
                || record.getId() == null) {
            throw new OrderBusinessException("退款记录创建失败");
        }

        return record;
    }

    private void checkOrder(Orders order) {

        if (order == null
                || !Orders.FUNDS_WECHAT.equals(order.getFundsSource())
                || !Orders.CANCELLED.equals(order.getStatus())
                || (!Orders.REFUNDING.equals(order.getPayStatus())
                && !Orders.REFUND.equals(order.getPayStatus()))
                || !Orders.wechatPay.equals(order.getPayMethod())
                || StrUtil.isBlank(order.getNumber())
                || StrUtil.isBlank(order.getTransactionId())) {

            throw new OrderBusinessException("订单不满足微信退款条件");
        }

        toFen(order.getAmount());
        PaymentRecord payment = paymentRecordMapper.selectOne(Wrappers.<PaymentRecord>lambdaQuery()
                .eq(PaymentRecord::getOrderId, order.getId()));
        if (payment == null || !PaymentRecord.SUCCESS.equals(payment.getStatus())
                || !Objects.equals(order.getNumber(), payment.getPayNumber())
                || !Objects.equals(order.getTransactionId(), payment.getTransactionId())
                || payment.getAmount() == null || payment.getAmount().compareTo(order.getAmount()) != 0) {
            throw new OrderBusinessException("缺少对应的成功支付记录，退款需人工核验");
        }
    }

    private long toFen(BigDecimal amount) {

        if (amount == null || amount.signum() <= 0) {
            throw new OrderBusinessException("退款金额无效");
        }

        try {
            // 与现有微信工具类的金额范围保持一致。
            return amount.movePointRight(2).intValueExact();
        } catch (ArithmeticException e) {
            throw new OrderBusinessException("退款金额精度或范围无效");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public RefundRecord claimQuery(String number) {
        RefundRecord record = pendingRecordForUpdate(number);
        LocalDateTime now = LocalDateTime.now().withNano(0);
        if (record == null || RefundRecord.SUCCESS.equals(record.getStatus())
                || RefundRecord.MANUAL.equals(record.getStatus())
                || record.getNextRetryTime() == null || record.getNextRetryTime().isAfter(now)) return null;
        if (record.getQueryCount() >= RecoveryPolicy.MAX_QUERIES) {
            manual(record, "QUERY_LIMIT");
            return null;
        }
        int count = record.getQueryCount() + 1;
        LocalDateTime next = RecoveryPolicy.nextTime(now, count);
        if (refundRecordMapper.update(null, Wrappers.<RefundRecord>lambdaUpdate()
                .eq(RefundRecord::getId, record.getId())
                .in(RefundRecord::getStatus, RefundRecord.PENDING, RefundRecord.PROCESSING)
                .set(RefundRecord::getQueryCount, count).set(RefundRecord::getNextRetryTime, next)
                .set(RefundRecord::getUpdateTime, now)) != 1) {
            throw new OrderBusinessException("退款查询计划更新失败");
        }
        record.setQueryCount(count);
        record.setNextRetryTime(next);
        return record;
    }

    @Transactional(rollbackFor = Exception.class)
    public RefundRecord claimAttempt(String number, Integer expectedQueryCount) {
        RefundRecord record = pendingRecordForUpdate(number);
        if (record == null || RefundRecord.SUCCESS.equals(record.getStatus())
                || RefundRecord.MANUAL.equals(record.getStatus())
                || !Objects.equals(record.getQueryCount(), expectedQueryCount)) return null;
        if (StrUtil.isNotBlank(record.getRefundId())) {
            manual(record, "ACCEPTED_REFUND_NOT_FOUND");
            return null;
        }
        if (record.getAttemptCount() >= RecoveryPolicy.MAX_REFUND_ATTEMPTS) {
            manual(record, "ATTEMPT_LIMIT");
            return null;
        }
        int count = record.getAttemptCount() + 1;
        if (refundRecordMapper.update(null, Wrappers.<RefundRecord>lambdaUpdate()
                .eq(RefundRecord::getId, record.getId())
                .in(RefundRecord::getStatus, RefundRecord.PENDING, RefundRecord.PROCESSING)
                .set(RefundRecord::getAttemptCount, count)
                .set(RefundRecord::getUpdateTime, LocalDateTime.now().withNano(0))) != 1) {
            throw new OrderBusinessException("退款申请次数更新失败");
        }
        record.setAttemptCount(count);
        return record;
    }

    private RefundRecord pendingRecordForUpdate(String number) {
        Orders order = ordersMapper.selectByNumberForUpdate(number);
        if (order == null || !Orders.FUNDS_WECHAT.equals(order.getFundsSource())
                || !Orders.CANCELLED.equals(order.getStatus())
                || !Orders.REFUNDING.equals(order.getPayStatus())) return null;
        checkOrder(order);
        return ensureRecord(order);
    }

    private void manual(RefundRecord record, String error) {
        if (refundRecordMapper.update(null, Wrappers.<RefundRecord>lambdaUpdate()
                .eq(RefundRecord::getId, record.getId())
                .in(RefundRecord::getStatus, RefundRecord.PENDING, RefundRecord.PROCESSING)
                .set(RefundRecord::getStatus, RefundRecord.MANUAL)
                .set(RefundRecord::getNextRetryTime, null).set(RefundRecord::getLastError, error)
                .set(RefundRecord::getUpdateTime, LocalDateTime.now().withNano(0))) != 1) {
            throw new OrderBusinessException("退款核查状态更新失败");
        }
    }
}
