package com.dineflow.order.service;

import cn.hutool.core.bean.BeanUtil;
import com.dineflow.constant.MessageConstant;
import com.dineflow.dto.OrderSubmitItemDTO;
import com.dineflow.dto.OrdersSubmitDTO;
import com.dineflow.entity.AddressBook;
import com.dineflow.entity.OrderDetail;
import com.dineflow.entity.Orders;
import com.dineflow.entity.ShoppingCart;
import com.dineflow.exception.AddressBookBusinessException;
import com.dineflow.exception.OrderBusinessException;
import com.dineflow.exception.ShoppingCartBusinessException;
import com.dineflow.mapper.AddressBookMapper;
import com.dineflow.mapper.OrderDetailMapper;
import com.dineflow.mapper.OrdersMapper;
import com.dineflow.mapper.ShoppingCartMapper;
import com.dineflow.service.IShoppingCartService;
import com.dineflow.utils.ThreadLocalUtil;
import com.dineflow.vo.OrderPreviewVO;
import com.dineflow.vo.OrderSubmitVO;
import com.dineflow.vo.ShoppingCartItemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class OrderCreateTxService {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("99999999.99");

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final ShoppingCartMapper shoppingCartMapper;
    private final AddressBookMapper addressBookMapper;
    private final OrdersMapper ordersMapper;
    private final OrderDetailMapper orderDetailMapper;
    private final IShoppingCartService shoppingCartService;

    /**
     * 查询商品当前信息并计算报价。
     * <p>
     * 此方法本身不开启事务：
     * 预览时直接调用；
     * 创建订单时在已开启的事务中调用。
     */
    public OrderPreviewVO calculateQuote() {

        List<ShoppingCartItemVO> items = shoppingCartService.showShoppingCart();

        if (items.isEmpty()) {
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        if (items.size() > 100) {
            throw new ShoppingCartBusinessException("购物车最多支持100条商品");
        }

        BigDecimal goodsAmount = ZERO;
        int totalNumber = 0;

        for (ShoppingCartItemVO item : items) {

            if (!Boolean.TRUE.equals(item.getAvailable())) {
                throw new ShoppingCartBusinessException("购物车中含失效商品：" + item.getUnavailableReason());
            }

            Integer number = item.getNumber();

            if (number == null || number < 1 || number > 99) {
                throw new OrderBusinessException("单项商品数量必须为1～99份");
            }

            if (item.getCartVersion() == null || item.getCartVersion() < 0 || isInvalidMoney(item.getCurrentPrice())) {
                throw new OrderBusinessException("商品价格或购物车版本异常");
            }

            BigDecimal subtotal = item.getCurrentPrice().multiply(BigDecimal.valueOf(number));

            item.setSubtotal(subtotal);

            goodsAmount = goodsAmount.add(subtotal);

            if (goodsAmount.compareTo(MAX_AMOUNT) > 0) {
                throw new OrderBusinessException("订单金额超出允许范围");
            }

            totalNumber += number;
        }

        OrderPreviewVO quote = new OrderPreviewVO();

        quote.setItems(items);
        quote.setGoodsAmount(goodsAmount);

        // 当前阶段打包费、配送费均为0。
        quote.setPackAmount(ZERO);
        quote.setDeliveryAmount(ZERO);
        quote.setPayableAmount(goodsAmount);

        quote.setTotalNumber(totalNumber);

        return quote;
    }

    /**
     * 创建订单的数据库短事务。
     * <p>
     * checkedAddress：事务外进行地图校验时使用的地址。
     * orderNumber：事务外通过Redis生成的订单号。
     */
    @Transactional(
            rollbackFor = Exception.class,
            isolation = Isolation.REPEATABLE_READ
    )
    public OrderSubmitVO createOrder(
            Long userId,
            OrdersSubmitDTO dto,
            AddressBook checkedAddress,
            String orderNumber,
            String requestHash) {

        if (userId == null || !Objects.equals(userId, ThreadLocalUtil.getCurrentId())) {
            throw new OrderBusinessException(MessageConstant.USER_NOT_LOGIN);
        }

        validateSubmitParams(dto);

        if (dto.getRequestId() == null
                || requestHash == null
                || !requestHash.matches("[0-9a-f]{64}")) {
            throw new OrderBusinessException("缺少有效的下单请求标识");
        }

        // 1. 先锁定本人整车，保留RR索引范围锁。
        List<ShoppingCart> lockedCarts = shoppingCartMapper.lockOwnedCart(userId);

        if (lockedCarts.isEmpty()) {
            throw new OrderBusinessException("购物车已变化，请重新预览并确认");
        }

        // 2. 再锁定本人收货地址。
        AddressBook currentAddress = addressBookMapper.lockOwnedAddress(dto.getAddressBookId(), userId);

        if (checkedAddress == null
                || currentAddress == null
                || !Objects.equals(
                dto.getAddressVersion(),
                currentAddress.getVersion()
        )
                || !sameAddress(checkedAddress, currentAddress)) {
            throw new AddressBookBusinessException("收货地址已变化或被删除，请重新加载并确认");
        }

        // 3. 锁定后重新报价并比较用户确认内容。
        OrderPreviewVO latestQuote = calculateQuote();

        checkConfirmedQuote(dto, latestQuote);

        // 获取锁可能等待，写单前再次检查预约时间。
        validateSubmitParams(dto);

        // 4. 创建订单。
        Orders order = BeanUtil.copyProperties(dto, Orders.class);

        order.setRequestId(dto.getRequestId());
        order.setRequestHash(requestHash);

        // 自动餐具只写入订单，不修改用于计算摘要的DTO。
        order.setTablewareNumber(
                Objects.equals(dto.getTablewareStatus(), 1)
                        ? latestQuote.getTotalNumber()
                        : dto.getTablewareNumber()
        );

        order.setUserId(userId);
        order.setAddressBookId(checkedAddress.getId());
        order.setNumber(orderNumber);
        order.setStatus(Orders.PENDING_PAYMENT);
        order.setPayStatus(Orders.UN_PAID);
        order.setFundsSource(Orders.FUNDS_WECHAT);
        order.setOrderTime(LocalDateTime.now().withNano(0));

        order.setPhone(checkedAddress.getPhone());
        order.setConsignee(checkedAddress.getConsignee());
        order.setAddress(buildWholeAddress(checkedAddress));

        order.setAmount(latestQuote.getPayableAmount());
        order.setPackAmount(latestQuote.getPackAmount());

        if (ordersMapper.insert(order) != 1 || order.getId() == null) {
            throw new OrderBusinessException("订单创建失败");
        }

        // 5. 保存本次成交快照。
        for (ShoppingCartItemVO item : latestQuote.getItems()) {

            OrderDetail detail = new OrderDetail();

            detail.setOrderId(order.getId());
            detail.setDishId(item.getDishId());
            detail.setSetmealId(item.getSetmealId());
            detail.setName(item.getName());
            detail.setImage(item.getImage());
            detail.setDishFlavor(item.getDishFlavor());
            detail.setNumber(item.getNumber());
            detail.setAmount(item.getCurrentPrice());

            if (orderDetailMapper.insert(detail) != 1) {
                throw new OrderBusinessException("订单明细保存失败");
            }
        }

        // 6. 精确消费本次成交的购物车行。
        for (ShoppingCart row : lockedCarts) {

            int deleted = shoppingCartMapper.deleteOwnedCartAtVersion(
                    row.getId(),
                    userId,
                    row.getVersion(),
                    row.getNumber()
            );

            if (deleted != 1) {
                throw new OrderBusinessException("购物车已变化，请重新预览并确认");
            }
        }

        return OrderSubmitVO.builder()
                .id(order.getId())
                .orderNumber(order.getNumber())
                .orderAmount(order.getAmount())
                .orderTime(order.getOrderTime())
                .build();
    }

    /**
     * 地图校验和订单保存共用同一地址拼接规则。
     */
    public String buildWholeAddress(AddressBook address) {

        if (address == null
                || blank(address.getConsignee())
                || blank(address.getPhone())
                || blank(address.getDetail())
                || (blank(address.getProvinceName())
                && blank(address.getCityName()))) {
            throw new AddressBookBusinessException("收货地址信息不完整，请修改后再提交");
        }

        return text(address.getProvinceName())
                + text(address.getCityName())
                + text(address.getDistrictName())
                + text(address.getDetail());
    }

    /**
     * 比较地图检查过的地址与事务内锁定的当前地址。
     * <p>
     * 不比较isDefault：
     * 只切换默认地址不会改变本次收件内容。
     */
    private boolean sameAddress(AddressBook checked, AddressBook current) {

        return Objects.equals(checked.getId(), current.getId())
                && Objects.equals(checked.getUserId(), current.getUserId())
                && Objects.equals(checked.getVersion(), current.getVersion())
                && Objects.equals(checked.getConsignee(), current.getConsignee())
                && Objects.equals(checked.getPhone(), current.getPhone())
                && Objects.equals(checked.getSex(), current.getSex())
                && Objects.equals(checked.getProvinceName(), current.getProvinceName())
                && Objects.equals(checked.getCityName(), current.getCityName())
                && Objects.equals(checked.getDistrictName(), current.getDistrictName())
                && Objects.equals(checked.getDetail(), current.getDetail());
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isInvalidMoney(BigDecimal value) {
        return value == null
                || value.signum() <= 0
                || value.compareTo(MAX_AMOUNT) > 0
                || value.stripTrailingZeros().scale() > 2;
    }

    /**
     * 校验并规范化下单业务参数。
     */
    public void validateSubmitParams(OrdersSubmitDTO dto) {

        if (dto == null) {
            throw new OrderBusinessException("下单参数不能为空");
        }

        if (dto.getPayMethod() != Orders.wechatPay) {
            throw new OrderBusinessException("暂不支持该支付方式");
        }

        if (dto.getRemark() != null) {
            String remark = dto.getRemark().trim();

            if (remark.length() > 100) {
                throw new OrderBusinessException("订单备注不能超过100字符");
            }

            dto.setRemark(remark);
        }

        if (!Objects.equals(dto.getDeliveryStatus(), 0) && !Objects.equals(dto.getDeliveryStatus(), 1)) {
            throw new OrderBusinessException("配送类型必须为0（预约）或1（即时）");
        }

        if (Objects.equals(dto.getDeliveryStatus(), 0)) {
            if (dto.getEstimatedDeliveryTime() == null || !dto.getEstimatedDeliveryTime().isAfter(LocalDateTime.now())) {
                throw new OrderBusinessException("预约配送时间必须晚于当前时间");
            }
        } else {
            dto.setEstimatedDeliveryTime(null);
        }

        if (!Objects.equals(dto.getTablewareStatus(), 0) && !Objects.equals(dto.getTablewareStatus(), 1)) {
            throw new OrderBusinessException("餐具类型必须为0（手动）或1（按餐量）");
        }

        if (Objects.equals(dto.getTablewareStatus(), 0)
                && (dto.getTablewareNumber() == null
                || dto.getTablewareNumber() < 0
                || dto.getTablewareNumber() > 6)) {
            throw new OrderBusinessException("手动选择的餐具数量必须为0～6份");
        }
    }

    /**
     * 比较用户确认的数据与后端本次报价。
     */
    public void checkConfirmedQuote(OrdersSubmitDTO dto, OrderPreviewVO latestQuote) {

        List<OrderSubmitItemDTO> confirmedItems = dto.getItems();

        if (confirmedItems == null
                || confirmedItems.isEmpty()
                || confirmedItems.size() > 100
                || isInvalidMoney(dto.getAmount())) {
            throw new OrderBusinessException("缺少有效的商品或金额确认信息，请重新预览");
        }

        Map<Long, OrderSubmitItemDTO> confirmedMap = new HashMap<>();

        for (OrderSubmitItemDTO item : confirmedItems) {

            if (item == null
                    || item.getCartId() == null
                    || item.getCartId() <= 0
                    || item.getCartVersion() == null
                    || item.getCartVersion() < 0
                    || item.getNumber() == null
                    || item.getNumber() < 1
                    || item.getNumber() > 99
                    || isInvalidMoney(item.getCurrentPrice())) {
                throw new OrderBusinessException("商品确认信息不合法，请重新预览");
            }

            if (confirmedMap.putIfAbsent(item.getCartId(), item) != null) {
                throw new OrderBusinessException("商品确认信息重复");
            }
        }

        // 用户确认清单必须与当前整车对应。
        if (confirmedMap.size() != latestQuote.getItems().size()) {
            throw new OrderBusinessException("购物车已变化，请重新预览并确认");
        }

        for (ShoppingCartItemVO latest : latestQuote.getItems()) {

            OrderSubmitItemDTO confirmed = confirmedMap.get(latest.getCartId());

            if (confirmed == null
                    || !Objects.equals(confirmed.getCartVersion(), latest.getCartVersion())
                    || !Objects.equals(confirmed.getDishId(), latest.getDishId())
                    || !Objects.equals(confirmed.getSetmealId(), latest.getSetmealId())
                    || !Objects.equals(confirmed.getDishFlavor(), latest.getDishFlavor())
                    || !Objects.equals(confirmed.getNumber(), latest.getNumber())) {
                throw new OrderBusinessException("购物车已变化，请重新预览并确认");
            }

            if (confirmed.getCurrentPrice().compareTo(latest.getCurrentPrice()) != 0) {
                throw new OrderBusinessException("商品价格已变化，请重新预览并确认");
            }
        }

        if (dto.getAmount().compareTo(latestQuote.getPayableAmount()) != 0) {
            throw new OrderBusinessException("订单金额已变化，请重新预览并确认");
        }
    }
}
