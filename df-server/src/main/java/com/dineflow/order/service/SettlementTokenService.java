package com.dineflow.order.service;

import static com.dineflow.constant.BusinessErrorCode.*;
import static com.dineflow.order.support.SettlementErrors.error;

import com.dineflow.dto.OrdersSubmitDTO;
import com.dineflow.dto.SelectedCartItemDTO;
import com.dineflow.entity.AddressBook;
import com.dineflow.entity.ShoppingCart;
import com.dineflow.model.SettlementTokenPayload;
import com.dineflow.order.model.OrderPriceResult;
import com.dineflow.order.model.SettlementItem;
import com.dineflow.properties.OrderSettlementProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

import javax.annotation.PostConstruct;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** HMAC-SHA256 的用户绑定报价。金额摘要与内容摘要分开，识别总价不变的逐项改价。 */
@Service
@RequiredArgsConstructor
public class SettlementTokenService {
    private final ObjectMapper json;
    private final OrderSettlementProperties properties;
    private byte[] key;
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    @PostConstruct
    public void validateConfiguration() {
        try {
            key = Base64.getDecoder().decode(Objects.requireNonNull(properties.getSecret()));
        } catch (RuntimeException e) {
            throw new IllegalStateException("必须配置 DINEFLOW_SETTLEMENT_SECRET（Base64 密钥）");
        }
        if (key.length < 32
                || properties.getTtl() == null
                || properties.getTtl().getSeconds() < 1
                || properties.getTtl().compareTo(Duration.ofHours(1)) > 0)
            throw new IllegalStateException("报价密钥至少32字节，有效期为1秒至1小时");
    }

    public record IssuedToken(String token, LocalDateTime expiresAt) {}

    /** 预览时签发：把用户确认的地址、购物车版本、价格和商品内容写入凭据。 */
    public IssuedToken issue(
            Long userId, AddressBook address, List<SettlementItem> items, OrderPriceResult price) {
        SettlementTokenPayload payload = new SettlementTokenPayload();
        payload.setSchemaVersion(1);
        payload.setUserId(userId);
        payload.setAddressBookId(address.getId());
        payload.setAddressVersion(address.getVersion());

        List<SettlementTokenPayload.CartItem> cartItems = new ArrayList<>();
        for (SettlementItem item : sortedItems(items)) {
            SettlementTokenPayload.CartItem cartItem = new SettlementTokenPayload.CartItem();
            cartItem.setCartItemId(item.getCartId());
            cartItem.setCartVersion(item.getCartVersion());
            cartItem.setNumber(item.getNumber());
            cartItems.add(cartItem);
        }
        payload.setItems(cartItems);
        payload.setPriceDigest(priceDigest(items, price));
        payload.setContentDigest(contentDigest(items));
        payload.setAmount(price.getAmount());
        payload.setIssuedAt(Instant.now().getEpochSecond());
        payload.setExpiresAt(payload.getIssuedAt() + properties.getTtl().getSeconds());

        try {
            // token 由“报价内容.签名”组成。编码不是加密，不要在内容中放敏感信息。
            String body = ENCODER.encodeToString(json.writeValueAsBytes(payload));
            String signature = ENCODER.encodeToString(sign(body));
            LocalDateTime expiresAt =
                    LocalDateTime.ofInstant(
                            Instant.ofEpochSecond(payload.getExpiresAt()),
                            ZoneId.of("Asia/Shanghai"));
            return new IssuedToken(body + "." + signature, expiresAt);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("报价编码失败", exception);
        }
    }

    private List<SettlementItem> sortedItems(List<SettlementItem> items) {
        // 在副本上排序，不改变调用方的商品展示顺序。
        List<SettlementItem> sorted = new ArrayList<>(items);
        sorted.sort(Comparator.comparing(SettlementItem::getCartId));
        return sorted;
    }

    /** 提交时先验签，再读取内容。先读取未经验证的内容会信任客户端伪造的数据。 */
    public SettlementTokenPayload verify(String token, Long userId) {
        if (token == null || token.length() > 65536) {
            throw error(INVALID_SETTLEMENT);
        }
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 2) {
                throw error(INVALID_SETTLEMENT);
            }
            byte[] expectedSignature = sign(parts[0]);
            byte[] actualSignature = DECODER.decode(parts[1]);
            // 使用标准库的比较方法，避免自己编写密码学算法。
            if (!MessageDigest.isEqual(expectedSignature, actualSignature)) {
                throw error(INVALID_SETTLEMENT);
            }

            SettlementTokenPayload payload =
                    json.readValue(DECODER.decode(parts[0]), SettlementTokenPayload.class);
            validatePayload(payload, userId);
            checkExpiry(payload);
            return payload;
        } catch (java.io.IOException | IllegalArgumentException exception) {
            throw error(INVALID_SETTLEMENT);
        }
    }

    private void validatePayload(SettlementTokenPayload payload, Long userId) {
        if (payload == null
                || payload.getSchemaVersion() != 1
                || userId == null
                || !Objects.equals(userId, payload.getUserId())) {
            throw error(INVALID_SETTLEMENT);
        }
        if (payload.getAddressBookId() == null
                || payload.getAddressVersion() == null
                || payload.getAmount() == null
                || payload.getPriceDigest() == null
                || payload.getContentDigest() == null) {
            throw error(INVALID_SETTLEMENT);
        }
        if (payload.getIssuedAt() == null
                || payload.getExpiresAt() == null
                || payload.getExpiresAt() <= payload.getIssuedAt()
                || payload.getIssuedAt() > Instant.now().getEpochSecond()) {
            throw error(INVALID_SETTLEMENT);
        }
        if (payload.getItems() == null
                || payload.getItems().isEmpty()
                || payload.getItems().size() > 100) {
            throw error(INVALID_SETTLEMENT);
        }
        Set<Long> cartIds = new HashSet<>();
        for (SettlementTokenPayload.CartItem item : payload.getItems()) {
            if (item == null
                    || item.getCartItemId() == null
                    || item.getCartVersion() == null
                    || item.getNumber() == null
                    || !cartIds.add(item.getCartItemId())) {
                throw error(INVALID_SETTLEMENT);
            }
        }
    }

    /** 验签成功还不够，请求中提交的地址、金额和条目也必须对应这一张报价。 */
    public void bind(SettlementTokenPayload payload, OrdersSubmitDTO dto) {
        if (!Objects.equals(payload.getAddressBookId(), dto.getAddressBookId())
                || !Objects.equals(payload.getAddressVersion(), dto.getAddressVersion())
                || payload.getAmount().compareTo(dto.getConfirmedAmount()) != 0
                || payload.getItems().size() != dto.getSelectedCartItems().size()) {
            throw error(INVALID_SETTLEMENT);
        }
        Map<Long, SettlementTokenPayload.CartItem> signedItems = itemMap(payload);
        for (SelectedCartItemDTO requested : dto.getSelectedCartItems()) {
            SettlementTokenPayload.CartItem signed = signedItems.get(requested.getCartItemId());
            if (signed == null
                    || !Objects.equals(signed.getCartVersion(), requested.getCartVersion())) {
                throw error(INVALID_SETTLEMENT);
            }
        }
    }

    /** 拿到数据库行锁后，检查购物车是否仍然是报价时的版本和数量。 */
    public void checkCart(SettlementTokenPayload payload, List<ShoppingCart> carts) {
        if (payload.getItems().size() != carts.size()) {
            throw error(CART_CHANGED);
        }
        Map<Long, SettlementTokenPayload.CartItem> signedItems = itemMap(payload);
        for (ShoppingCart cart : carts) {
            SettlementTokenPayload.CartItem signed = signedItems.get(cart.getId());
            if (signed == null
                    || !Objects.equals(signed.getCartVersion(), cart.getVersion())
                    || !Objects.equals(signed.getNumber(), cart.getNumber())) {
                throw error(CART_CHANGED);
            }
        }
    }

    private Map<Long, SettlementTokenPayload.CartItem> itemMap(SettlementTokenPayload payload) {
        Map<Long, SettlementTokenPayload.CartItem> result = new HashMap<>();
        for (SettlementTokenPayload.CartItem item : payload.getItems()) {
            result.put(item.getCartItemId(), item);
        }
        return result;
    }

    public void checkQuote(
            SettlementTokenPayload p, List<SettlementItem> items, OrderPriceResult price) {
        if (!Objects.equals(p.getPriceDigest(), priceDigest(items, price)))
            throw error(PRICE_CHANGED);
        if (!Objects.equals(p.getContentDigest(), contentDigest(items))) throw error(ITEM_CHANGED);
    }

    public void checkExpiry(SettlementTokenPayload p) {
        if (p.getExpiresAt() == null || Instant.now().getEpochSecond() >= p.getExpiresAt())
            throw error(SETTLEMENT_EXPIRED);
    }

    private String priceDigest(List<SettlementItem> items, OrderPriceResult price) {
        List<Object> rows = new ArrayList<>();
        for (SettlementItem i : sortedItems(items))
            rows.add(
                    List.of(
                            i.getCartId(),
                            i.getNumber(),
                            money(i.getUnitPrice()),
                            money(i.getSubtotal())));
        return digest(
                List.of(
                        rows,
                        money(price.getGoodsAmount()),
                        money(price.getPackAmount()),
                        money(price.getDeliveryFee()),
                        money(price.getDiscountAmount()),
                        money(price.getAmount())));
    }

    private String contentDigest(List<SettlementItem> items) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SettlementItem i : sortedItems(items)) {
            Map<String, Object> row = new TreeMap<>();
            row.put("cartId", i.getCartId());
            row.put("dishId", i.getDishId());
            row.put("setmealId", i.getSetmealId());
            row.put("name", i.getName());
            row.put("image", i.getImage());
            row.put("dishFlavor", i.getDishFlavor());
            row.put("flavorDefinition", i.getFlavorDefinition());
            row.put("composition", i.getSetmealItemsSnapshot());
            rows.add(row);
        }
        return digest(rows);
    }

    private String money(java.math.BigDecimal n) {
        return n.setScale(2).toPlainString();
    }

    private String digest(Object value) {
        try {
            return ENCODER.encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(value)));
        } catch (Exception e) {
            throw new IllegalStateException("报价摘要失败", e);
        }
    }

    private byte[] sign(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(body.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception e) {
            throw new IllegalStateException("报价签名失败", e);
        }
    }
}
