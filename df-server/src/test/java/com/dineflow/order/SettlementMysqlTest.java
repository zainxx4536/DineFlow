package com.dineflow.order;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.dineflow.dto.*;
import com.dineflow.entity.*;
import com.dineflow.exception.BaseException;
import com.dineflow.mapper.*;
import com.dineflow.order.service.*;
import com.dineflow.order.support.*;
import com.dineflow.properties.*;
import com.dineflow.service.impl.OrdersServiceImpl;
import com.dineflow.service.impl.DishServiceImpl;
import com.dineflow.service.impl.AddressBookServiceImpl;
import com.dineflow.model.Coordinate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.dineflow.utils.*;
import com.dineflow.vo.SettlementPreviewVO;
import com.dineflow.websocket.WebSocketServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 只允许专用隔离实例；Mapper、MySQL、Spring 事务均为真实组件。 */
@EnabledIfEnvironmentVariable(named = "DINEFLOW_TEST_JDBC_URL", matches = "jdbc:mysql://127\\.0\\.0\\.1:33308/iteration1_test.*")
class SettlementMysqlTest {
    static AnnotationConfigApplicationContext context;
    static JdbcTemplate jdbc;
    static CartMutationService carts;
    static OrdersServiceImpl orders;
    static OrderCreateTxService create;
    static OrderSettlementProperties properties;
    static final AtomicLong numbers = new AtomicLong();

    @BeforeAll
    static void start() {
        context = new AnnotationConfigApplicationContext(Config.class);
        jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        carts = context.getBean(CartMutationService.class);
        orders = context.getBean(OrdersServiceImpl.class);
        create = context.getBean(OrderCreateTxService.class);
        properties = context.getBean(OrderSettlementProperties.class);
    }

    @AfterAll
    static void close() {
        if (context != null) context.close();
    }

    @BeforeEach
    void reset() {
        jdbc.execute("DROP TRIGGER IF EXISTS fail_detail");
        jdbc.execute("DROP TRIGGER IF EXISTS fail_cart_delete");
        for (String table : List.of("order_detail", "orders", "shopping_cart", "dish_flavor", "setmeal_dish", "setmeal", "dish", "address_book", "user")) {
            jdbc.update("DELETE FROM " + table);
        }
        jdbc.update("INSERT INTO user(id,openid) VALUES(1,'test1'),(2,'test2')");
        jdbc.update("INSERT INTO address_book(id,user_id,phone,consignee,province_name,city_name,district_name,detail) VALUES(1,1,'13800000000','测试','省','市','区','路')");
        jdbc.update("INSERT INTO dish(id,name,category_id,price,status) VALUES(1,'菜品甲',1,10,1),(2,'菜品乙',1,20,1)");
        properties.setPackFee(BigDecimal.ZERO);
        properties.setDeliveryFee(BigDecimal.ZERO);
        properties.setDiscountAmount(BigDecimal.ZERO);
        properties.setTtl(Duration.ofMinutes(10));
        ThreadLocalUtil.setCurrentId(1L);
        // 每个场景恢复外部依赖；数据库和事务始终是真实组件。
        BaiduMapClient maps = context.getBean(BaiduMapClient.class);
        org.mockito.Mockito.reset(maps);
        when(maps.getCoordinate(anyString())).thenReturn(Coordinate.builder().lng(106.5).lat(29.5).build());
        when(maps.getDrivingDistance(any())).thenReturn(100.0);
        when(context.getBean(StringRedisTemplate.class).opsForValue().get(anyString())).thenReturn("1");
        when(context.getBean(RedisIdWorker.class).nextId(anyString())).thenAnswer(call -> numbers.incrementAndGet());
    }

    @AfterEach
    void cleanupThread() {
        ThreadLocalUtil.removeCurrentId();
    }

    ShoppingCartDTO dish(long id, String flavor) {
        ShoppingCartDTO dto = new ShoppingCartDTO();
        dto.setDishId(id);
        dto.setDishFlavor(flavor);
        return dto;
    }

    OrdersSubmitDTO quote() {
        List<Long> ids = jdbc.queryForList("SELECT id FROM shopping_cart WHERE user_id=1 ORDER BY id", Long.class);
        SettlementPreviewDTO request = new SettlementPreviewDTO();
        request.setAddressBookId(1L);
        request.setCartItemId(ids);
        SettlementPreviewVO preview = orders.preview(request);
        OrdersSubmitDTO submit = new OrdersSubmitDTO();
        submit.setAddressBookId(preview.getAddressBookId());
        submit.setAddressVersion(preview.getAddressVersion());
        submit.setConfirmedAmount(preview.getAmount());
        submit.setSettlementToken(preview.getSettlementToken());
        List<SelectedCartItemDTO> selected = new ArrayList<>();
        for (var item : preview.getItems()) {
            SelectedCartItemDTO row = new SelectedCartItemDTO();
            row.setCartItemId(item.getCartId());
            row.setCartVersion(item.getCartVersion());
            selected.add(row);
        }
        submit.setSelectedCartItems(selected);
        submit.setPayMethod(1);
        submit.setDeliveryStatus(1);
        submit.setTablewareStatus(1);
        return submit;
    }

    void submit(OrdersSubmitDTO request) {
        create.createOrder(1L, request, "test-" + numbers.incrementAndGet());
    }

    void fails(String code, Runnable action) {
        BaseException error = assertThrows(BaseException.class, action::run);
        assertEquals(code, error.getErrorCode());
    }

    int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    @Test
    void concurrentFirstAddCreatesOneRowAndTwoPortions() throws Exception {
        runTogether(() -> carts.add(1L, dish(1, ""), 1), () -> carts.add(1L, dish(1, null), 1));
        assertEquals(1, count("shopping_cart"));
        assertEquals(2, jdbc.queryForObject("SELECT number FROM shopping_cart", Integer.class));
    }

    @Test
    void changedUnitPricesCannotHideBehindSameTotal() {
        carts.add(1L, dish(1, ""), 1);
        carts.add(1L, dish(2, ""), 1);
        OrdersSubmitDTO request = quote();
        jdbc.update("UPDATE dish SET price=14 WHERE id=1");
        jdbc.update("UPDATE dish SET price=16 WHERE id=2");
        fails("PRICE_CHANGED", () -> submit(request));
        assertEquals(0, count("orders"));
        assertEquals(2, count("shopping_cart"));
    }

    @Test
    void samePriceContentChangeIsRejected() {
        carts.add(1L, dish(1, ""), 1);
        OrdersSubmitDTO request = quote();
        jdbc.update("UPDATE dish SET name='新名称' WHERE id=1");
        fails("ITEM_CHANGED", () -> submit(request));
    }

    @Test
    void tokenIsBoundToUserAndCannotBeTampered() {
        carts.add(1L, dish(1, ""), 1);
        OrdersSubmitDTO request = quote();
        SettlementTokenService tokens = context.getBean(SettlementTokenService.class);
        fails("INVALID_SETTLEMENT", () -> tokens.verify(request.getSettlementToken(), 2L));
        request.setSettlementToken("x" + request.getSettlementToken());
        fails("INVALID_SETTLEMENT", () -> submit(request));
    }

    @Test
    void addressAndCartVersionsRemainRequired() {
        carts.add(1L, dish(1, ""), 1);
        OrdersSubmitDTO request = quote();
        jdbc.update("UPDATE address_book SET version=version+1 WHERE id=1");
        fails("ADDRESS_CHANGED", () -> submit(request));
        OrdersSubmitDTO refreshed = quote();
        carts.add(1L, dish(1, ""), 1);
        fails("CART_CHANGED", () -> submit(refreshed));
    }

    @Test
    void idModeMustReferToSameProductAndVersion() {
        carts.add(1L, dish(1, ""), 1);
        ShoppingCartDTO wrong = dish(2, "");
        wrong.setId(jdbc.queryForObject("SELECT id FROM shopping_cart", Long.class));
        wrong.setVersion(1L);
        fails("CART_CHANGED", () -> carts.add(1L, wrong, 1));
        wrong.setDishId(1L);
        carts.add(1L, wrong, 1);
        fails("CART_CHANGED", () -> carts.add(1L, wrong, 1));
        assertEquals(2, jdbc.queryForObject("SELECT number FROM shopping_cart", Integer.class));
    }

    @Test
    void selectedRowsAreConsumedExactlyOnce() throws Exception {
        carts.add(1L, dish(1, ""), 1);
        OrdersSubmitDTO request = quote();
        carts.add(1L, dish(2, ""), 1);
        List<String> results = Collections.synchronizedList(new ArrayList<>());
        Runnable attempt = () -> {
            try { submit(request); results.add("OK"); }
            catch (BaseException error) { results.add(error.getErrorCode()); }
        };
        runTogether(attempt, attempt);
        assertEquals(1, Collections.frequency(results, "OK"));
        assertEquals(1, Collections.frequency(results, "CART_CHANGED"));
        assertEquals(1, count("orders"));
        assertEquals(1, count("order_detail"));
        assertEquals(2L, jdbc.queryForObject("SELECT dish_id FROM shopping_cart", Long.class));
    }

    @Test
    void detailInsertFailureRollsBackWholeOrder() {
        carts.add(1L, dish(1, ""), 1);
        OrdersSubmitDTO request = quote();
        jdbc.execute("CREATE TRIGGER fail_detail BEFORE INSERT ON order_detail FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected detail failure'");
        assertThrows(Exception.class, () -> submit(request));
        assertEquals(0, count("orders"));
        assertEquals(0, count("order_detail"));
        assertEquals(1, count("shopping_cart"));
    }

    @Test
    void cartDeleteFailureRollsBackInsertedOrderAndDetails() {
        carts.add(1L, dish(1, ""), 1);
        OrdersSubmitDTO request = quote();
        jdbc.execute("CREATE TRIGGER fail_cart_delete BEFORE DELETE ON shopping_cart FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected delete failure'");
        assertThrows(Exception.class, () -> submit(request));
        assertEquals(0, count("orders"));
        assertEquals(0, count("order_detail"));
        assertEquals(1, count("shopping_cart"));
    }

    @Test
    void setmealSnapshotContainsCurrentComponentsAndRemainsHistorical() {
        jdbc.update("INSERT INTO setmeal(id,name,category_id,price,status) VALUES(1,'套餐',1,25,1)");
        jdbc.update("INSERT INTO setmeal_dish(setmeal_id,dish_id,name,price,copies) VALUES(1,1,'旧缓存名',1,2)");
        ShoppingCartDTO meal = new ShoppingCartDTO();
        meal.setSetmealId(1L);
        carts.add(1L, meal, 1);
        submit(quote());
        String snapshot = jdbc.queryForObject("SELECT setmeal_items_snapshot FROM order_detail", String.class);
        assertTrue(snapshot.contains("菜品甲"));
        assertTrue(snapshot.contains("\"copies\":2"));
        jdbc.update("UPDATE dish SET name='以后改名',status=0 WHERE id=1");
        assertEquals(snapshot, jdbc.queryForObject("SELECT setmeal_items_snapshot FROM order_detail", String.class));
        fails("ITEM_NOT_AVAILABLE", () -> carts.add(1L, meal, 1));
    }

    @Test
    void feeConfigurationIsSharedAndChangesInvalidateQuote() {
        properties.setPackFee(new BigDecimal("1.50"));
        properties.setDeliveryFee(new BigDecimal("2.00"));
        carts.add(1L, dish(1, ""), 1);
        OrdersSubmitDTO request = quote();
        assertEquals(new BigDecimal("13.50"), request.getConfirmedAmount());
        properties.setDeliveryFee(new BigDecimal("3.00"));
        fails("PRICE_CHANGED", () -> submit(request));
        submit(quote());
        assertEquals(new BigDecimal("14.50"), jdbc.queryForObject("SELECT amount FROM orders", BigDecimal.class));
    }

    @Test
    void flavorOrderIsCanonicalAndIllegalSelectionsAreRejected() {
        jdbc.update("INSERT INTO dish_flavor(dish_id,name,value) VALUES(1,'辣度','[\"微辣\",\"中辣\"]'),(1,'温度','[\"热\",\"冷\"]')");
        carts.add(1L, dish(1, "热,微辣"), 1);
        carts.add(1L, dish(1, " 微辣 , 热 "), 1);
        assertEquals(1, count("shopping_cart"));
        fails("ITEM_NOT_AVAILABLE", () -> carts.add(1L, dish(1, "微辣"), 1));
        fails("ITEM_NOT_AVAILABLE", () -> carts.add(1L, dish(1, "未知,热"), 1));
        submit(quote());
    }

    @Test
    void waitingForLockCannotExtendExpiredQuote() throws Exception {
        properties.setTtl(Duration.ofSeconds(2));
        carts.add(1L, dish(1, ""), 1);
        OrdersSubmitDTO request = quote();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> holder = pool.submit(() -> new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).execute(status -> {
                context.getBean(SettlementLocks.class).cartOwner(1L);
                locked.countDown();
                await(release);
                return null;
            }));
            assertTrue(locked.await(5, TimeUnit.SECONDS));
            Future<?> waiting = pool.submit(() -> fails("SETTLEMENT_EXPIRED", () -> submit(request)));
            Thread.sleep(2300);
            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
            waiting.get(10, TimeUnit.SECONDS);
            assertEquals(0, count("orders"));
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void oneMoreOrderPreservesDistinctFlavorsAndUsesCurrentPrice() {
        jdbc.update("INSERT INTO dish_flavor(dish_id,name,value) VALUES(1,'辣度','[\"微辣\",\"中辣\"]')");
        carts.add(1L, dish(1, "微辣"), 1);
        carts.add(1L, dish(1, "中辣"), 2);
        submit(quote());
        long orderId = jdbc.queryForObject("SELECT id FROM orders", Long.class);
        jdbc.update("UPDATE orders SET status=5 WHERE id=?", orderId);
        jdbc.update("UPDATE dish SET price=15 WHERE id=1");
        orders.oneMoreOrder(orderId);
        assertEquals(2, count("shopping_cart"));
        assertEquals(3, jdbc.queryForObject("SELECT SUM(number) FROM shopping_cart", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM shopping_cart WHERE amount=15", Integer.class));
    }

    @Test
    void oneUnavailableItemRollsBackEntireOneMoreOrder() {
        carts.add(1L, dish(1, ""), 1);
        carts.add(1L, dish(2, ""), 1);
        submit(quote());
        long orderId = jdbc.queryForObject("SELECT id FROM orders", Long.class);
        jdbc.update("UPDATE orders SET status=5 WHERE id=?", orderId);
        jdbc.update("UPDATE dish SET status=0 WHERE id=2");
        fails("ITEM_NOT_AVAILABLE", () -> orders.oneMoreOrder(orderId));
        assertEquals(0, count("shopping_cart"));
    }

    @Test
    void twoDecrementsCannotCreateNegativeQuantity() throws Exception {
        carts.add(1L, dish(1, ""), 1);
        ShoppingCartDTO request = dish(1, "");
        request.setId(jdbc.queryForObject("SELECT id FROM shopping_cart", Long.class));
        request.setVersion(1L);
        List<String> results = Collections.synchronizedList(new ArrayList<>());
        Runnable attempt = () -> {
            try { carts.sub(1L, request); results.add("OK"); }
            catch (BaseException error) { results.add(error.getErrorCode()); }
        };
        runTogether(attempt, attempt);
        assertEquals(1, Collections.frequency(results, "OK"));
        assertEquals(1, Collections.frequency(results, "CART_CHANGED"));
        assertEquals(0, count("shopping_cart"));
    }

    @Test
    void administratorWaitsForSettlementCatalogReadLock() throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch writerStarted = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> holder = pool.submit(() -> new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).execute(status -> {
                context.getBean(SettlementLocks.class).catalogRead();
                locked.countDown();
                await(release);
                return null;
            }));
            assertTrue(locked.await(5, TimeUnit.SECONDS));
            Future<?> writer = pool.submit(() -> {
                writerStarted.countDown();
                context.getBean(DishServiceImpl.class).modifyDishStatus(1L, 0);
            });
            assertTrue(writerStarted.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> writer.get(300, TimeUnit.MILLISECONDS));
            assertEquals(1, jdbc.queryForObject("SELECT status FROM dish WHERE id=1", Integer.class));
            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
            writer.get(10, TimeUnit.SECONDS);
            assertEquals(0, jdbc.queryForObject("SELECT status FROM dish WHERE id=1", Integer.class));
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void submitOrchestrationCallsMapOutsideTransaction() {
        carts.add(1L, dish(1, ""), 1);
        OrdersSubmitDTO request = quote();
        when(context.getBean(BaiduMapClient.class).getCoordinate(anyString())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return Coordinate.builder().lng(106.5).lat(29.5).build();
        });
        orders.submitOrder(request);
        assertEquals(1, count("orders"));
        assertEquals(0, count("shopping_cart"));
    }

    @Test
    void mapFailureRejectsOrderWithoutConsumingCart() {
        carts.add(1L, dish(1, ""), 1);
        OrdersSubmitDTO request = quote();
        when(context.getBean(BaiduMapClient.class).getCoordinate(anyString()))
                .thenThrow(new RuntimeException("simulated map timeout"));
        fails("MAP_SERVICE_UNAVAILABLE", () -> orders.submitOrder(request));
        assertEquals(0, count("orders"));
        assertEquals(1, count("shopping_cart"));
    }

    @Test
    void invalidMapDistanceAndOutOfRangeRejectOrder() {
        carts.add(1L, dish(1, ""), 1);
        OrdersSubmitDTO request = quote();
        BaiduMapClient maps = context.getBean(BaiduMapClient.class);
        for (double distance : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY}) {
            when(maps.getDrivingDistance(any())).thenReturn(distance);
            fails("MAP_SERVICE_UNAVAILABLE", () -> orders.submitOrder(request));
        }
        when(maps.getDrivingDistance(any())).thenReturn(5001.0);
        fails("DELIVERY_OUT_OF_RANGE", () -> orders.submitOrder(request));
        assertEquals(0, count("orders"));
        assertEquals(1, count("shopping_cart"));
    }

    @Test
    void shopClosureRejectsPreviewAndSubmit() {
        carts.add(1L, dish(1, ""), 1);
        OrdersSubmitDTO request = quote();
        when(context.getBean(StringRedisTemplate.class).opsForValue().get(anyString())).thenReturn("0");
        fails("SHOP_CLOSED", () -> quote());
        fails("SHOP_CLOSED", () -> orders.submitOrder(request));
        assertEquals(0, count("orders"));
    }

    @Test
    void shopClosureDuringMapCallIsRechecked() {
        carts.add(1L, dish(1, ""), 1);
        OrdersSubmitDTO request = quote();
        when(context.getBean(BaiduMapClient.class).getDrivingDistance(any())).thenAnswer(call -> {
            when(context.getBean(StringRedisTemplate.class).opsForValue().get(anyString())).thenReturn("0");
            return 100.0;
        });
        fails("SHOP_CLOSED", () -> orders.submitOrder(request));
        assertEquals(0, count("orders"));
    }

    @Test
    void previewRejectsMissingForeignAndDuplicateCartIds() {
        carts.add(1L, dish(1, ""), 1);
        carts.add(2L, dish(2, ""), 1);
        long own = jdbc.queryForObject("SELECT id FROM shopping_cart WHERE user_id=1", Long.class);
        long foreign = jdbc.queryForObject("SELECT id FROM shopping_cart WHERE user_id=2", Long.class);
        SettlementPreviewDTO request = new SettlementPreviewDTO();
        request.setAddressBookId(1L);
        request.setCartItemId(List.of(own, own));
        fails("INVALID_CART_ITEM", () -> orders.preview(request));
        request.setCartItemId(List.of(foreign));
        fails("CART_CHANGED", () -> orders.preview(request));
        request.setCartItemId(List.of(Long.MAX_VALUE));
        fails("CART_CHANGED", () -> orders.preview(request));
    }

    @Test
    void previewUsesCurrentProductPriceInsteadOfCartCache() {
        carts.add(1L, dish(1, ""), 1);
        jdbc.update("UPDATE shopping_cart SET amount=1");
        jdbc.update("UPDATE dish SET price=32 WHERE id=1");
        assertEquals(new BigDecimal("32.00"), quote().getConfirmedAmount());
        jdbc.update("UPDATE dish SET status=0 WHERE id=1");
        fails("ITEM_NOT_AVAILABLE", () -> quote());
    }

    @Test
    void signedQuoteCannotBeBoundToAnotherAddressOrSelection() {
        carts.add(1L, dish(1, ""), 1);
        carts.add(1L, dish(2, ""), 1);
        OrdersSubmitDTO address = quote();
        address.setAddressBookId(2L);
        fails("INVALID_SETTLEMENT", () -> submit(address));
        OrdersSubmitDTO selected = quote();
        selected.setSelectedCartItems(List.of(selected.getSelectedCartItems().get(0)));
        fails("INVALID_SETTLEMENT", () -> submit(selected));
        OrdersSubmitDTO price = quote();
        price.setConfirmedAmount(new BigDecimal("0.01"));
        fails("INVALID_SETTLEMENT", () -> submit(price));
        assertEquals(0, count("orders"));
    }

    @Test
    void secondDetailFailureRollsBackFirstInsertedDetail() {
        carts.add(1L, dish(1, ""), 1);
        carts.add(1L, dish(2, ""), 1);
        OrdersSubmitDTO request = quote();
        jdbc.execute("CREATE TRIGGER fail_detail BEFORE INSERT ON order_detail FOR EACH ROW BEGIN IF NEW.dish_id=2 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='second detail failure'; END IF; END");
        assertThrows(Exception.class, () -> submit(request));
        assertEquals(0, count("orders"));
        assertEquals(0, count("order_detail"));
        assertEquals(2, count("shopping_cart"));
    }

    @Test
    void cartIdDoesNotOverwriteExistingDetailPrimaryKey() {
        carts.add(1L, dish(1, ""), 1);
        long cartId = jdbc.queryForObject("SELECT id FROM shopping_cart", Long.class);
        jdbc.update("INSERT INTO order_detail(id,order_id,dish_id,name,number,amount) VALUES(?,999,2,'历史明细',1,20)", cartId);
        submit(quote());
        assertEquals(2, count("order_detail"));
        assertEquals("历史明细", jdbc.queryForObject("SELECT name FROM order_detail WHERE id=?", String.class, cartId));
    }

    @Test
    void samePriceSetmealCompositionChangeRequiresConfirmation() {
        jdbc.update("INSERT INTO setmeal(id,name,category_id,price,status) VALUES(1,'套餐',1,25,1)");
        jdbc.update("INSERT INTO setmeal_dish(setmeal_id,dish_id,copies) VALUES(1,1,1)");
        ShoppingCartDTO meal = new ShoppingCartDTO();
        meal.setSetmealId(1L);
        carts.add(1L, meal, 1);
        OrdersSubmitDTO request = quote();
        jdbc.update("UPDATE setmeal_dish SET dish_id=2 WHERE setmeal_id=1");
        fails("ITEM_CHANGED", () -> submit(request));
        assertEquals(0, count("orders"));
        assertEquals(new BigDecimal("25.00"), quote().getConfirmedAmount());
        jdbc.update("UPDATE setmeal SET status=0 WHERE id=1");
        fails("ITEM_NOT_AVAILABLE", () -> quote());
        jdbc.update("UPDATE setmeal SET status=1 WHERE id=1");
        jdbc.update("DELETE FROM setmeal_dish");
        fails("ITEM_NOT_AVAILABLE", () -> quote());
    }

    @Test
    void staleCasCannotRecreateConsumedRow() {
        carts.add(1L, dish(1, ""), 1);
        OrdersSubmitDTO request = quote();
        ShoppingCartDTO stale = dish(1, "");
        stale.setId(request.getSelectedCartItems().get(0).getCartItemId());
        stale.setVersion(request.getSelectedCartItems().get(0).getCartVersion());
        submit(request);
        fails("CART_CHANGED", () -> carts.add(1L, stale, 1));
        assertEquals(0, count("shopping_cart"));
        carts.add(1L, dish(1, ""), 1);
        assertEquals(1, count("shopping_cart"));
    }

    @Test
    void addressEditDuringMapCallInvalidatesPreviouslyCheckedAddress() {
        carts.add(1L, dish(1, ""), 1);
        OrdersSubmitDTO request = quote();
        when(context.getBean(BaiduMapClient.class).getDrivingDistance(any())).thenAnswer(call -> {
            AddressBookServiceImpl addresses = context.getBean(AddressBookServiceImpl.class);
            AddressBook edited = addresses.getAddressById(1L);
            edited.setDetail("新地址");
            addresses.modifyAddressById(edited);
            return 100.0;
        });
        fails("ADDRESS_CHANGED", () -> orders.submitOrder(request));
        assertEquals(0, count("orders"));
    }

    @Test
    void addressDeleteBeforeSubmitPreventsOrder() {
        carts.add(1L, dish(1, ""), 1);
        OrdersSubmitDTO request = quote();
        context.getBean(AddressBookServiceImpl.class).deleteAddressById(1L);
        fails("ADDRESS_CHANGED", () -> submit(request));
        assertEquals(0, count("orders"));
    }

    @Test
    void addressWriterAndSubmitResolveToVersionConflict() throws Exception {
        carts.add(1L, dish(1, ""), 1);
        OrdersSubmitDTO request = quote();
        CountDownLatch edited = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> writer = pool.submit(() -> new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).execute(status -> {
                ThreadLocalUtil.setCurrentId(1L);
                try {
                    AddressBookServiceImpl addresses = context.getBean(AddressBookServiceImpl.class);
                    AddressBook address = addresses.getAddressById(1L);
                    address.setDetail("并发新地址");
                    addresses.modifyAddressById(address);
                    edited.countDown();
                    await(release);
                    return null;
                } finally { ThreadLocalUtil.removeCurrentId(); }
            }));
            assertTrue(edited.await(5, TimeUnit.SECONDS));
            Future<?> waiting = pool.submit(() -> fails("ADDRESS_CHANGED", () -> submit(request)));
            assertThrows(TimeoutException.class, () -> waiting.get(250, TimeUnit.MILLISECONDS));
            release.countDown();
            writer.get(10, TimeUnit.SECONDS);
            waiting.get(10, TimeUnit.SECONDS);
            assertEquals(0, count("orders"));
        } finally { release.countDown(); pool.shutdownNow(); }
    }

    void runTogether(Runnable first, Runnable second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> a = pool.submit(() -> { await(start); first.run(); });
            Future<?> b = pool.submit(() -> { await(start); second.run(); });
            start.countDown();
            a.get(15, TimeUnit.SECONDS);
            b.get(15, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
    }

    static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test latch timeout");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        }
    }

    @TestConfiguration
    @EnableTransactionManagement(proxyTargetClass = true)
    @MapperScan("com.dineflow.mapper")
    @Import({SettlementLocks.class, CartMutationService.class, FlavorSelection.class,
            OrderSettlementService.class, OrderPriceCalculator.class, SettlementTokenService.class,
            OrderCreateTxService.class, OrdersServiceImpl.class, DishServiceImpl.class, AddressBookServiceImpl.class})
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource(System.getenv("DINEFLOW_TEST_JDBC_URL"), "root", "");
        }
        @Bean PlatformTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource source) throws Exception {
            MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(source);
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            factory.setConfiguration(configuration);
            GlobalConfig global = new GlobalConfig();
            global.setDbConfig(new GlobalConfig.DbConfig().setIdType(IdType.AUTO));
            factory.setGlobalConfig(global);
            factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/*.xml"));
            return factory.getObject();
        }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean OrderSettlementProperties properties() {
            OrderSettlementProperties result = new OrderSettlementProperties();
            byte[] secret = new byte[32];
            new java.security.SecureRandom().nextBytes(secret);
            result.setSecret(Base64.getEncoder().encodeToString(secret));
            return result;
        }
        @Bean StringRedisTemplate redis() {
            StringRedisTemplate redis = mock(StringRedisTemplate.class);
            ValueOperations<String, String> values = mock(ValueOperations.class);
            when(redis.opsForValue()).thenReturn(values);
            when(values.get(anyString())).thenReturn("1");
            return redis;
        }
        @Bean RedisIdWorker ids() { return mock(RedisIdWorker.class); }
        @Bean WeChatProperties wechat() { return new WeChatProperties(); }
        @Bean BaiduMapClient maps() { return mock(BaiduMapClient.class); }
        @Bean WebSocketServer sockets() { return mock(WebSocketServer.class); }
    }
}
