package com.dineflow.order.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.dineflow.entity.Orders;
import com.dineflow.entity.PaymentRecord;
import com.dineflow.exception.OrderBusinessException;
import com.dineflow.mapper.OrdersMapper;
import com.dineflow.mapper.PaymentRecordMapper;
import com.dineflow.properties.WeChatProperties;
import com.dineflow.vo.OrderDetailVO;
import com.wechat.pay.java.service.payments.model.Transaction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 支付意图及查询计划的短事务；统一先锁订单，再处理关联支付记录。
 */
@Service
@RequiredArgsConstructor
public class PaymentRecordTxService {

    private final OrdersMapper ordersMapper;

    private final PaymentRecordMapper paymentRecordMapper;

    private final WeChatProperties weChatProperties;

    /**
     * 在微信预下单之前提交支付意图，复用原支付号与金额。
     */
    @Transactional(rollbackFor = Exception.class)
    public PaymentRecord prepare(String number, Long userId) {
        Orders order = ordersMapper.selectByNumberForUpdate(number);
        if (order == null || !Objects.equals(order.getUserId(), userId)) {
            throw new OrderBusinessException("订单不存在");
        }
        requireManaged(order);
        if (!Orders.PENDING_PAYMENT.equals(order.getStatus())
                || !Orders.UN_PAID.equals(order.getPayStatus())
                || !Orders.wechatPay.equals(order.getPayMethod())
                || expired(order)) {
            throw new OrderBusinessException("订单状态已变化或支付已超时，请刷新");
        }
        PaymentRecord record = find(order);
        if (record == null) {
            LocalDateTime now = LocalDateTime.now().withNano(0);
            record = new PaymentRecord();
            record.setOrderId(order.getId());
            record.setPayNumber(order.getNumber());
            record.setAmount(order.getAmount());
            record.setStatus(PaymentRecord.PENDING);
            record.setQueryCount(0);
            record.setNextQueryTime(now.plusMinutes(1));
            record.setCreateTime(now);
            record.setUpdateTime(now);
            if (paymentRecordMapper.insert(record) != 1 || record.getId() == null) {
                throw new OrderBusinessException("支付记录创建失败");
            }
        }
        check(order, record);
        if (!PaymentRecord.PENDING.equals(record.getStatus())) {
            throw new OrderBusinessException("支付结果已变化，请刷新订单");
        }
        return record;
    }

    // 调用方已锁定订单；只读已有意图，不在成功通知中补造意图。
    public PaymentRecord getRecord(Orders order) {
        requireManaged(order);
        PaymentRecord record = find(order);
        check(order, record);
        return record;
    }

    /**
     * 先提交查询次数和下一次时间，返回后外层才调用微信。
     */
    @Transactional(rollbackFor = Exception.class)
    public PaymentRecord claim(String number) {
        Orders order = ordersMapper.selectByNumberForUpdate(number);
        if (order == null || !Orders.FUNDS_WECHAT.equals(order.getFundsSource())) return null;
        PaymentRecord record = getRecord(order);
        LocalDateTime now = LocalDateTime.now().withNano(0);
        if (!PaymentRecord.PENDING.equals(record.getStatus())
                || record.getNextQueryTime() == null
                || record.getNextQueryTime().isAfter(now)) return null;
        if (record.getQueryCount() >= RecoveryPolicy.MAX_QUERIES) {
            manual(record, "QUERY_LIMIT");
            return null;
        }
        int count = record.getQueryCount() + 1;
        LocalDateTime next = RecoveryPolicy.nextTime(now, count);
        int changed = paymentRecordMapper.update(null, Wrappers.<PaymentRecord>lambdaUpdate()
                .eq(PaymentRecord::getId, record.getId())
                .eq(PaymentRecord::getStatus, PaymentRecord.PENDING)
                .set(PaymentRecord::getQueryCount, count)
                .set(PaymentRecord::getNextQueryTime, next)
                .set(PaymentRecord::getUpdateTime, now));
        if (changed != 1) throw new OrderBusinessException("支付查询计划更新失败");
        record.setQueryCount(count);
        record.setNextQueryTime(next);
        return record;
    }

    /**
     * 成功结果交给paySuccess；这里处理可信查单的其他状态。
     */
    @Transactional(rollbackFor = Exception.class)
    public void applyNonSuccess(String number, Transaction result) {
        Orders order = ordersMapper.selectByNumberForUpdate(number);
        PaymentRecord record = getRecord(order);
        if (result == null || result.getTradeState() == null
                || !Objects.equals(number, result.getOutTradeNo())
                || !Objects.equals(weChatProperties.getMchid(), result.getMchid())
                || !Objects.equals(weChatProperties.getAppid(), result.getAppid())) {
            throw new OrderBusinessException("支付查询关联不一致");
        }
        if (result.getAmount() != null
                && (!"CNY".equals(result.getAmount().getCurrency())
                || !Objects.equals(order.getAmount().movePointRight(2).intValueExact(),
                result.getAmount().getTotal()))) {
            throw new OrderBusinessException("支付查询金额不一致");
        }
        if (!PaymentRecord.PENDING.equals(record.getStatus())) return;
        switch (result.getTradeState()) {
            case CLOSED -> {
                int changed = paymentRecordMapper.update(null, Wrappers.<PaymentRecord>lambdaUpdate()
                        .eq(PaymentRecord::getId, record.getId())
                        .eq(PaymentRecord::getStatus, PaymentRecord.PENDING)
                        .set(PaymentRecord::getStatus, PaymentRecord.CLOSED)
                        .set(PaymentRecord::getNextQueryTime, null)
                        .set(PaymentRecord::getLastError, "WECHAT_CLOSED")
                        .set(PaymentRecord::getUpdateTime, LocalDateTime.now().withNano(0)));
                if (changed != 1) throw new OrderBusinessException("支付关闭结果更新失败");
                cancel(order, "支付订单已关闭");
            }
            case NOTPAY -> {
                if (expired(order)) cancel(order, "订单超时未支付");
                if (record.getQueryCount() >= RecoveryPolicy.MAX_QUERIES) manual(record, "QUERY_LIMIT");
            }
            case USERPAYING, ACCEPT -> {
                if (record.getQueryCount() >= RecoveryPolicy.MAX_QUERIES) manual(record, "QUERY_LIMIT");
            }
            default -> manual(record, "UNEXPECTED_" + result.getTradeState().name());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void notFound(String number) {
        Orders order = ordersMapper.selectByNumberForUpdate(number);
        PaymentRecord record = getRecord(order);
        if (!PaymentRecord.PENDING.equals(record.getStatus())) return;
        if (expired(order)) cancel(order, "订单超时未支付");
        saveError(record, "ORDER_NOT_EXIST");
    }

    @Transactional(rollbackFor = Exception.class)
    public void rememberError(String number, String error) {
        Orders order = ordersMapper.selectByNumberForUpdate(number);
        if (order == null || !Orders.FUNDS_WECHAT.equals(order.getFundsSource())) return;
        PaymentRecord record = find(order);
        if (record != null && PaymentRecord.PENDING.equals(record.getStatus())) saveError(record, error);
    }

    /**
     * 与支付意图创建锁同一订单，锁内再次确认确实没有支付记录。
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancelWithoutPayment(String number) {
        Orders order = ordersMapper.selectByNumberForUpdate(number);
        if (order == null || !Orders.FUNDS_WECHAT.equals(order.getFundsSource()) || !expired(order)) return;
        if (find(order) == null) cancel(order, "订单超时未支付");
    }

    public List<PaymentRecord> pendingRecords() {
        return paymentRecordMapper.selectList(Wrappers.<PaymentRecord>lambdaQuery()
                .eq(PaymentRecord::getStatus, PaymentRecord.PENDING)
                .inSql(PaymentRecord::getOrderId, "SELECT id FROM orders WHERE funds_source='WECHAT'")
                .le(PaymentRecord::getNextQueryTime, LocalDateTime.now())
                .orderByAsc(PaymentRecord::getNextQueryTime, PaymentRecord::getId).last("LIMIT 50"));
    }

    public List<Orders> overdueWithoutPayment() {
        return ordersMapper.selectList(Wrappers.<Orders>lambdaQuery()
                .eq(Orders::getFundsSource, Orders.FUNDS_WECHAT)
                .eq(Orders::getStatus, Orders.PENDING_PAYMENT)
                .eq(Orders::getPayStatus, Orders.UN_PAID)
                .le(Orders::getOrderTime, LocalDateTime.now().minusMinutes(15))
                .notExists("SELECT 1 FROM payment_record p WHERE p.order_id=orders.id")
                .orderByAsc(Orders::getOrderTime, Orders::getId).last("LIMIT 50"));
    }

    public void fillPaymentInfo(OrderDetailVO order) {
        if (!Orders.FUNDS_WECHAT.equals(order.getFundsSource())) {
            order.setPaymentMessage("历史订单，资金状态仅供查询");
            return;
        }
        PaymentRecord record = find(order);
        if (record == null) return;
        check(order, record);
        order.setPaymentStatus(record.getStatus());
        order.setPaymentMessage(switch (record.getStatus()) {
            case 0 -> "支付结果待确认";
            case 1 -> "支付成功";
            case 2 -> "支付已关闭";
            case 3 -> "支付结果待人工核查，请联系商家";
            default -> throw new OrderBusinessException("支付记录状态无效");
        });
    }

    private PaymentRecord find(Orders order) {
        return paymentRecordMapper.selectOne(Wrappers.<PaymentRecord>lambdaQuery()
                .eq(PaymentRecord::getOrderId, order.getId()));
    }

    private void check(Orders order, PaymentRecord record) {
        if (record == null || !Objects.equals(order.getId(), record.getOrderId())
                || !Objects.equals(order.getNumber(), record.getPayNumber())
                || record.getAmount() == null || record.getAmount().signum() <= 0
                || record.getAmount().compareTo(order.getAmount()) != 0
                || record.getStatus() == null || !List.of(0, 1, 2, 3).contains(record.getStatus())
                || record.getQueryCount() == null || record.getQueryCount() < 0) {
            throw new OrderBusinessException("支付记录与订单不一致");
        }
    }

    private void requireManaged(Orders order) {
        if (order == null || !Orders.FUNDS_WECHAT.equals(order.getFundsSource())) {
            throw new OrderBusinessException("历史订单仅供查询，资金操作需人工核验");
        }
    }

    private boolean expired(Orders order) {
        return order.getOrderTime() == null
                || !order.getOrderTime().isAfter(LocalDateTime.now().minusMinutes(15));
    }

    private void cancel(Orders order, String reason) {
        if (!Orders.PENDING_PAYMENT.equals(order.getStatus()) || !Orders.UN_PAID.equals(order.getPayStatus())) return;
        Orders change = new Orders();
        change.setStatus(Orders.CANCELLED);
        change.setCancelReason(reason);
        change.setCancelTime(LocalDateTime.now().withNano(0));
        if (ordersMapper.update(change, Wrappers.<Orders>lambdaUpdate()
                .eq(Orders::getId, order.getId()).eq(Orders::getFundsSource, Orders.FUNDS_WECHAT)
                .eq(Orders::getStatus, Orders.PENDING_PAYMENT).eq(Orders::getPayStatus, Orders.UN_PAID)) != 1) {
            throw new OrderBusinessException("超时订单状态更新失败");
        }
    }

    private void saveError(PaymentRecord record, String error) {
        if (record.getQueryCount() >= RecoveryPolicy.MAX_QUERIES) {
            manual(record, error);
            return;
        }
        paymentRecordMapper.update(null, Wrappers.<PaymentRecord>lambdaUpdate()
                .eq(PaymentRecord::getId, record.getId()).eq(PaymentRecord::getStatus, PaymentRecord.PENDING)
                .set(PaymentRecord::getLastError, error)
                .set(PaymentRecord::getUpdateTime, LocalDateTime.now().withNano(0)));
    }

    private void manual(PaymentRecord record, String error) {
        if (paymentRecordMapper.update(null, Wrappers.<PaymentRecord>lambdaUpdate()
                .eq(PaymentRecord::getId, record.getId()).eq(PaymentRecord::getStatus, PaymentRecord.PENDING)
                .set(PaymentRecord::getStatus, PaymentRecord.MANUAL).set(PaymentRecord::getNextQueryTime, null)
                .set(PaymentRecord::getLastError, error)
                .set(PaymentRecord::getUpdateTime, LocalDateTime.now().withNano(0))) != 1) {
            throw new OrderBusinessException("支付核查状态更新失败");
        }
    }
}
