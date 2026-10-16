package com.dineflow.order.support;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.json.JSONConfig;
import cn.hutool.json.JSONUtil;
import com.dineflow.dto.OrderSubmitItemDTO;
import com.dineflow.dto.OrdersSubmitDTO;
import com.dineflow.entity.Orders;
import com.dineflow.exception.OrderBusinessException;
import com.dineflow.utils.CartItemKeyUtil;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

@Component
public class OrderRequestNormalizer {

    private static final BigDecimal MAX_AMOUNT =
            new BigDecimal("99999999.99");

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-"
                    + "[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 静态校验并规范化输入。
     * 返回独立副本，不修改调用方传入的原对象。
     */
    public OrdersSubmitDTO normalize(OrdersSubmitDTO source) {

        if (source == null) {
            throw new OrderBusinessException("下单参数不能为空");
        }

        if (source.getRequestId() == null
                || !UUID_PATTERN.matcher(source.getRequestId()).matches()) {
            throw new OrderBusinessException("requestId必须为标准UUID");
        }

        requireId(source.getAddressBookId(), "地址ID");
        requireVersion(source.getAddressVersion(), "地址版本");

        if (source.getPayMethod() != Orders.wechatPay) {
            throw new OrderBusinessException("暂不支持该支付方式");
        }

        if (isZeroOrOne(source.getDeliveryStatus())) {
            throw new OrderBusinessException("配送类型必须为0或1");
        }

        if (isZeroOrOne(source.getTablewareStatus())) {
            throw new OrderBusinessException("餐具类型必须为0或1");
        }

        String remark = source.getRemark() == null ? "" : source.getRemark().trim();

        if (remark.length() > 100) {
            throw new OrderBusinessException("订单备注不能超过100字符");
        }

        if (source.getItems() == null || source.getItems().isEmpty() || source.getItems().size() > 100) {
            throw new OrderBusinessException("确认商品条目必须为1～100条");
        }

        Set<Long> ids = new HashSet<>();
        List<OrderSubmitItemDTO> items = new ArrayList<>();

        for (OrderSubmitItemDTO original : source.getItems()) {

            if (original == null) {
                throw new OrderBusinessException("商品确认条目不能为空");
            }

            requireId(original.getCartId(), "购物车条目ID");
            requireVersion(original.getCartVersion(), "购物车版本");

            if (!ids.add(original.getCartId())) {
                throw new OrderBusinessException("商品确认信息重复");
            }

            if ((original.getDishId() == null) == (original.getSetmealId() == null)) {
                throw new OrderBusinessException("菜品ID和套餐ID必须且只能填写一个");
            }

            Long productId = original.getDishId() != null ? original.getDishId() : original.getSetmealId();

            requireId(productId, "商品ID");

            if (original.getNumber() == null || original.getNumber() < 1 || original.getNumber() > 99) {
                throw new OrderBusinessException("单项商品数量必须为1～99份");
            }

            String flavor = CartItemKeyUtil.normalizeFlavor(original.getDishFlavor());

            if (original.getSetmealId() != null && flavor != null) {
                throw new OrderBusinessException("套餐不能填写菜品口味");
            }

            OrderSubmitItemDTO item = new OrderSubmitItemDTO();

            item.setCartId(original.getCartId());
            item.setCartVersion(original.getCartVersion());
            item.setDishId(original.getDishId());
            item.setSetmealId(original.getSetmealId());
            item.setDishFlavor(flavor);
            item.setNumber(original.getNumber());
            item.setCurrentPrice(money(original.getCurrentPrice()));

            items.add(item);
        }

        // 不同的请求条目顺序，不应产生不同摘要。
        items.sort(Comparator.comparing(OrderSubmitItemDTO::getCartId));

        OrdersSubmitDTO result = new OrdersSubmitDTO();

        result.setRequestId(source.getRequestId().toLowerCase(Locale.ROOT));
        result.setAddressBookId(source.getAddressBookId());
        result.setAddressVersion(source.getAddressVersion());
        result.setPayMethod(source.getPayMethod());
        result.setRemark(remark);
        result.setAmount(money(source.getAmount()));
        result.setItems(items);
        result.setDeliveryStatus(source.getDeliveryStatus());

        if (Objects.equals(source.getDeliveryStatus(), 0)) {

            if (source.getEstimatedDeliveryTime() == null || source.getEstimatedDeliveryTime().getNano() != 0) {
                throw new OrderBusinessException("预约时间不能为空且必须精确到秒");
            }

            // 此处不检查是否晚于现在。
            // 已成功订单重放时，原预约时间可能已经过去。
            result.setEstimatedDeliveryTime(source.getEstimatedDeliveryTime());
        } else {
            result.setEstimatedDeliveryTime(null);
        }

        result.setTablewareStatus(source.getTablewareStatus());

        if (Objects.equals(source.getTablewareStatus(), 0)) {

            Integer number = source.getTablewareNumber();

            if (number == null || number < 0 || number > 6) {
                throw new OrderBusinessException("手动选择的餐具数量必须为0～6份");
            }

            result.setTablewareNumber(number);
        } else {
            // 自动餐具数量属于后端计算结果，不作为请求输入。
            result.setTablewareNumber(null);
        }

        return result;
    }

    /**
     * dto必须是normalize()返回的对象。
     */
    public String calculateHash(Long userId, OrdersSubmitDTO dto) {

        requireId(userId, "用户ID");

        List<List<Object>> lines = new ArrayList<>();

        for (OrderSubmitItemDTO item : dto.getItems()) {
            lines.add(Arrays.asList(
                    item.getCartId(),
                    item.getCartVersion(),
                    item.getDishId(),
                    item.getSetmealId(),
                    item.getDishFlavor(),
                    item.getNumber(),
                    item.getCurrentPrice().toPlainString()
            ));
        }

        List<Object> source = Arrays.asList(
                "checkout-v1",
                userId,
                lines,
                dto.getAddressBookId(),
                dto.getAddressVersion(),
                dto.getAmount().toPlainString(),
                dto.getPayMethod(),
                dto.getRemark(),
                dto.getDeliveryStatus(),
                dto.getEstimatedDeliveryTime() == null ? null : dto.getEstimatedDeliveryTime().format(TIME_FORMAT),
                dto.getTablewareStatus(),
                dto.getTablewareNumber()
        );

        // 必须保留null占位，避免dishId/setmealId等字段丢失位置。
        String canonicalJson = JSONUtil.toJsonStr(
                source,
                JSONConfig.create().setIgnoreNullValue(false)
        );

        return SecureUtil.sha256(canonicalJson);
    }

    private BigDecimal money(BigDecimal value) {

        if (value == null || value.signum() <= 0 || value.compareTo(MAX_AMOUNT) > 0) {
            throw new OrderBusinessException("确认金额必须大于0且在允许范围内");
        }

        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new OrderBusinessException(
                    "确认金额最多保留两位小数"
            );
        }
    }

    private boolean isZeroOrOne(Integer value) {
        return !Objects.equals(value, 0) && !Objects.equals(value, 1);
    }

    private void requireId(Long value, String name) {
        if (value == null || value <= 0) {
            throw new OrderBusinessException(name + "必须大于0");
        }
    }

    private void requireVersion(Integer value, String name) {
        if (value == null || value < 0) {
            throw new OrderBusinessException(name + "不能为负数或空");
        }
    }
}