package com.dineflow.order.service;

import com.dineflow.constant.MessageConstant;
import com.dineflow.dto.OrdersSubmitDTO;
import com.dineflow.dto.SelectedCartItemDTO;
import com.dineflow.entity.*;
import com.dineflow.exception.AddressBookBusinessException;
import com.dineflow.exception.OrderBusinessException;
import com.dineflow.exception.ShoppingCartBusinessException;
import com.dineflow.mapper.*;
import com.dineflow.order.model.OrderPriceResult;
import com.dineflow.order.model.SettlementItem;
import com.dineflow.order.support.OrderPriceCalculator;
import com.dineflow.vo.OrderSubmitVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderCreateTxService {

    private final ShoppingCartMapper shoppingCartMapper;

    private final AddressBookMapper addressBookMapper;

    private final SetmealMapper setmealMapper;

    private final DishMapper dishMapper;

    private final OrderSettlementService orderSettlementService;

    private final OrderPriceCalculator orderPriceCalculator;

    private final OrdersMapper ordersMapper;

    private final OrderDetailMapper orderDetailMapper;


    /**
     * 创建订单短事务
     * 这里只做本地数据库读写和最终成交校验，
     * 不在这里调用百度地图、Redis、微信支付等远程服务。
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderSubmitVO createOrder(Long userId, OrdersSubmitDTO dto, String orderNumber, String wholeAddress) {

        // 1. 锁定地址
        AddressBook addressBook = addressBookMapper.selectForUpdate(userId, dto.getAddressBookId());
        if (addressBook == null) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_CHANGED);
        }

        // 当前数据库中的地址版本，必须仍然等于用户提交的 addressVersion。
        if (!Objects.equals(addressBook.getVersion(), dto.getAddressVersion())) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_CHANGED);
        }

        // 2. 取得本次提交的购物车条目
        List<SelectedCartItemDTO> selectedItems = dto.getSelectedCartItems();
        if (selectedItems == null || selectedItems.isEmpty()) {
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        // 3. cartItemId 不能重复
        Set<Long> ids = new HashSet<>();

        for (SelectedCartItemDTO item : selectedItems) {
            if (item == null || item.getCartItemId() == null || item.getCartVersion() == null) {
                throw new ShoppingCartBusinessException(MessageConstant.CART_CHANGED);
            }

            if (!ids.add(item.getCartItemId())) {
                throw new ShoppingCartBusinessException(MessageConstant.CART_CHANGED);
            }
        }

        // 4. 固定顺序锁定购物车记录
        // 排序不能保证完全没有死锁，但可以降低多个事务锁顺序不同导致死锁的概率。
        List<Long> cartItemIds = ids.stream().sorted().toList();

        List<ShoppingCart> cartList = shoppingCartMapper.selectForUpdate(userId, cartItemIds);

        // 有记录被删除、不属于当前用户、ID不存在等情况
        if (cartList.size() != selectedItems.size()) {
            throw new ShoppingCartBusinessException(MessageConstant.CART_CHANGED);
        }

        // 5. 按条目 ID 比较锁定后的购物车版本，数量等内容变化由购物车写入口递增 version 来识别。
        Map<Long, SelectedCartItemDTO> selectedItemMap = selectedItems.stream()
                .collect(Collectors.toMap(SelectedCartItemDTO::getCartItemId, Function.identity()));

        for (ShoppingCart cart : cartList) {
            SelectedCartItemDTO selectedItem = selectedItemMap.get(cart.getId());
            if (selectedItem == null || !Objects.equals(cart.getVersion(), selectedItem.getCartVersion())) {
                throw new ShoppingCartBusinessException(MessageConstant.CART_CHANGED);
            }
        }

        // 6. 收集当前菜品 / 套餐 ID
        List<Long> dishIds = cartList.stream()
                .map(ShoppingCart::getDishId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();

        List<Long> setmealIds = cartList.stream()
                .map(ShoppingCart::getSetmealId)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();

        // 7. 锁定当前 Dish / Setmeal，最终成交价格不能使用 shopping_cart.amount，必须重新读取当前商品数据。
        List<Dish> dishes = dishIds.isEmpty() ? Collections.emptyList() : dishMapper.selectForUpdate(dishIds);

        List<Setmeal> setmeals = setmealIds.isEmpty() ? Collections.emptyList() : setmealMapper.selectForUpdate(setmealIds);

        // 8. 根据锁定后的当前商品重新构造结算项，这里得到的 SettlementItem 才是最终成交候选快照。
        List<SettlementItem> settlementItems = orderSettlementService.buildSettlementItems(cartList, dishes, setmeals);

        // TODO 9. 费用
        BigDecimal packAmount = BigDecimal.ZERO;
        BigDecimal deliveryFee = BigDecimal.ZERO;
        BigDecimal discountAmount = BigDecimal.ZERO;

        // 10. 服务端重新计算订单金额
        OrderPriceResult price = orderPriceCalculator.calculate(
                settlementItems,
                packAmount,
                deliveryFee,
                discountAmount
        );

        // 11. 检查最终金额是否发生变化
        if (dto.getConfirmedAmount() == null || price.getAmount().compareTo(dto.getConfirmedAmount()) != 0) {
            throw new OrderBusinessException(MessageConstant.PRICE_CHANGED);
        }

        // 12. 所有最终校验均通过，开始创建订单
        LocalDateTime now = LocalDateTime.now();

        Orders order = Orders.builder()
                // 服务端生成
                .number(orderNumber)
                .status(Orders.PENDING_PAYMENT)
                .userId(userId)
                .addressBookId(addressBook.getId())
                .orderTime(now)
                // 用户选择，但属于允许提交的业务字段
                .payMethod(dto.getPayMethod())
                .remark(dto.getRemark())
                .estimatedDeliveryTime(dto.getEstimatedDeliveryTime())
                .deliveryStatus(dto.getDeliveryStatus())
                .tablewareNumber(dto.getTablewareNumber())
                .tablewareStatus(dto.getTablewareStatus())
                // 服务端状态
                .payStatus(Orders.UN_PAID)
                // 服务端重新计算的最终金额
                .goodsAmount(price.getGoodsAmount())
                .packAmount(price.getPackAmount())
                .deliveryFee(price.getDeliveryFee())
                .discountAmount(price.getDiscountAmount())
                .amount(price.getAmount())
                // 地址成交快照
                .phone(addressBook.getPhone())
                .consignee(addressBook.getConsignee())
                .address(wholeAddress)
                .build();

        int orderRows = ordersMapper.insert(order);

        if (orderRows != 1) {
            throw new OrderBusinessException("订单创建失败");
        }

        // 13. 根据最终 SettlementItem 创建 OrderDetail
        // 不允许：BeanUtil.copyProperties(shoppingCart, detail)
        // 否则可能把 shopping_cart.id 等不属于订单明细的数据复制进来。
        List<OrderDetail> details = settlementItems.stream()
                .map(item ->
                        OrderDetail.builder()
                                // 订单主键
                                .orderId(order.getId())
                                // 商品标识
                                .dishId(item.getDishId())
                                .setmealId(item.getSetmealId())
                                // 最终成交快照
                                .name(item.getName())
                                .image(item.getImage())
                                .dishFlavor(item.getDishFlavor())
                                .number(item.getNumber())
                                // OrderDetail.amount 保存成交单价，不是 subtotal。
                                .amount(item.getUnitPrice())
                                // 套餐组成成交快照
                                .setmealItemsSnapshot(item.getSetmealItemsSnapshot())
                                .build()
                )
                .toList();

        // 14. 保存订单明细
        // 目前逐条 insert 即可。任意一条失败都会抛异常，整个 createOrder() 事务统一回滚。
        for (OrderDetail detail : details) {
            int affectedRows = orderDetailMapper.insert(detail);
            if (affectedRows != 1) {
                throw new OrderBusinessException("订单明细创建失败");
            }
        }

        // 15. 精确删除本次实际消费的购物车条目
        int deletedRows = shoppingCartMapper.deleteSelectedItems(userId, selectedItems);

        // 16. 删除数量必须完全一致
        if (deletedRows != selectedItems.size()) {
            throw new ShoppingCartBusinessException(MessageConstant.CART_CHANGED);
        }

        // 17. 返回订单结果
        return OrderSubmitVO.builder()
                .id(order.getId())
                .orderNumber(order.getNumber())
                .orderAmount(order.getAmount())
                .orderTime(order.getOrderTime())
                .build();
    }
}