package com.dineflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 参数：本人地址ID [same|different]，默认 same。
 * 环境变量：DINEFLOW_TEST_TOKEN；可选 DINEFLOW_TEST_BASE_URL。
 * 每种模式会创建一笔订单并消费购物车，运行前重新加购，运行期间不要修改购物车或地址。
 * 完整原请求保存到临时目录，不保存Token；结果未知时不要另起请求号。
 */
public class OrderSubmitConcurrencyCheck {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();
    private final String baseUrl;
    private final String token;

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2)
            throw new IllegalArgumentException("运行参数：本人地址ID [same|different]");
        String token = System.getenv("DINEFLOW_TEST_TOKEN");
        require(token != null && !token.isBlank(), "请设置DINEFLOW_TEST_TOKEN");
        String baseUrl = System.getenv("DINEFLOW_TEST_BASE_URL");
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "http://127.0.0.1:8080/user";
        new OrderSubmitConcurrencyCheck(baseUrl, token)
                .run(Long.parseLong(args[0]), args.length == 2 ? args[1] : "same");
    }

    OrderSubmitConcurrencyCheck(String baseUrl, String token) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.token = token;
    }

    void run(long addressId, String mode) throws Exception {
        require(addressId > 0, "地址ID必须大于0");
        require("same".equals(mode) || "different".equals(mode), "模式只能是same或different");
        String marker = "i2-" + mode + "-" + UUID.randomUUID().toString().substring(0, 12);
        ObjectNode first = confirmation(addressId, marker);
        ObjectNode second = first.deepCopy();
        if ("different".equals(mode)) second.put("requestId", UUID.randomUUID().toString());
        saveOriginalRequests(mode, marker, first, second);

        List<Reply> initial = parallel(first, second);
        for (int i = 0; i < initial.size(); i++)
            System.out.println("首次请求" + (i + 1) + "：" + initial.get(i).summary());
        if ("same".equals(mode)) checkSame(first, initial);
        else checkDifferent(first, second, initial);

        JsonNode cart = data(send("/shoppingCart/list", "GET", null));
        require(cart.isArray(), "购物车返回格式不正确");
        Set<String> consumed = new java.util.HashSet<>();
        first.path("items").forEach(item -> consumed.add(item.path("cartId").asText()));
        cart.forEach(item -> require(!consumed.contains(item.path("cartId").asText()),
                "已提交的购物车行仍然存在"));
        System.out.println("数据库验收标记remark：" + marker);
        System.out.println("SELECT id, number, request_id FROM orders WHERE remark = '" + marker + "';");
    }

    private ObjectNode confirmation(long addressId, String marker) throws Exception {
        JsonNode address = data(send("/addressBook/" + addressId, "GET", null));
        JsonNode quote = data(send("/order/preview", "POST", JSON.createObjectNode()));
        require(address.hasNonNull("version"), "地址未返回版本");
        require(quote.path("items").isArray() && !quote.path("items").isEmpty(), "购物车报价为空");
        ObjectNode body = JSON.createObjectNode();
        body.put("requestId", UUID.randomUUID().toString());
        body.put("addressBookId", addressId);
        body.set("addressVersion", address.get("version"));
        body.set("amount", quote.get("payableAmount"));
        body.put("payMethod", 1);
        body.put("deliveryStatus", 1);
        body.put("tablewareStatus", 1);
        body.putNull("tablewareNumber");
        body.put("remark", marker);
        ArrayNode items = body.putArray("items");
        for (JsonNode row : quote.path("items")) {
            ObjectNode item = items.addObject();
            for (String field : List.of("cartId", "cartVersion", "dishId", "setmealId",
                    "dishFlavor", "number", "currentPrice"))
                item.set(field, row.get(field));
        }
        return body;
    }

    private void checkSame(ObjectNode original, List<Reply> initial) throws Exception {
        JsonNode order = data(resolve(original));
        validateOrder(order);
        for (Reply reply : initial) {
            if (reply.success()) sameOrder(order, data(reply));
            else require(reply.rejected() || reply.uncertain(), "首次请求出现非预期响应");
        }

        // 购物车已消费后继续发完整原请求，不重新预览或生成UUID。
        sameOrder(order, data(resolve(original)));

        ObjectNode changed = original.deepCopy();
        changed.put("remark", original.path("remark").asText() + "-changed");
        Reply conflict = send("/order/submit", "POST", changed);
        require(conflict.rejected() && conflict.body().path("msg").asText().contains("请求号"),
                "同号不同内容未明确拒绝：" + conflict.summary());
        sameOrder(order, data(resolve(original)));
    }

    private void checkDifferent(ObjectNode first, ObjectNode second, List<Reply> initial) throws Exception {
        List<Reply> results = List.of(resolve(first), resolve(second));
        long successes = results.stream().filter(Reply::success).count();
        require(successes == 1, "不同请求号应只有一个创建结果，实际成功数=" + successes);
        for (int i = 0; i < results.size(); i++) {
            Reply result = results.get(i);
            if (result.success()) {
                validateOrder(data(result));
                if (initial.get(i).success()) sameOrder(data(result), data(initial.get(i)));
            } else {
                require(result.rejected(), "竞争失败请求结果仍未确认：" + result.summary());
                require(!initial.get(i).success(), "同一请求先返回成功、重放却失败");
            }
        }
    }

    private List<Reply> parallel(ObjectNode first, ObjectNode second) throws Exception {
        CompletableFuture<Reply> a = async(first);
        CompletableFuture<Reply> b = async(second);
        CompletableFuture.allOf(a, b).join();
        return List.of(a.join(), b.join());
    }

    private CompletableFuture<Reply> async(ObjectNode body) throws Exception {
        return client.sendAsync(request("/order/submit", "POST", body),
                        HttpResponse.BodyHandlers.ofString())
                .handle((response, failure) -> failure == null ? decode(response)
                        : new Reply(0, JSON.missingNode(), "网络结果未知"));
    }

    private Reply resolve(ObjectNode original) throws Exception {
        Reply reply = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            reply = send("/order/submit", "POST", original);
            if (reply.success() || reply.rejected()) return reply;
            if (attempt < 2) Thread.sleep(200);
        }
        throw new AssertionError("原请求结果仍未知，请保留已保存的请求文件：" + reply.summary());
    }

    private Reply send(String path, String method, ObjectNode body) throws Exception {
        try {
            return decode(client.send(request(path, method, body), HttpResponse.BodyHandlers.ofString()));
        } catch (java.io.IOException e) {
            return new Reply(0, JSON.missingNode(), "网络结果未知");
        }
    }

    private HttpRequest request(String path, String method, ObjectNode body) throws Exception {
        return HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(40))
                .header("authentication", token).header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                .build();
    }

    private static Reply decode(HttpResponse<String> response) {
        try {
            JsonNode body = JSON.readTree(response.body());
            return new Reply(response.statusCode(), body == null ? JSON.missingNode() : body, "");
        } catch (java.io.IOException e) {
            return new Reply(response.statusCode(), JSON.missingNode(), "响应格式异常");
        }
    }

    private static JsonNode data(Reply reply) {
        require(reply.success() && reply.body().hasNonNull("data"), "接口未成功：" + reply.summary());
        return reply.body().get("data");
    }

    private static void validateOrder(JsonNode order) {
        for (String field : List.of("id", "orderNumber", "orderAmount", "orderTime"))
            require(order.hasNonNull(field), "订单结果缺少字段：" + field);
        require(order.path("orderAmount").isNumber(), "成交金额必须是JSON数值");
    }

    private static void sameOrder(JsonNode expected, JsonNode actual) {
        validateOrder(expected);
        validateOrder(actual);
        for (String field : List.of("id", "orderNumber", "orderTime"))
            require(expected.path(field).asText().equals(actual.path(field).asText()),
                    "重放结果的" + field + "发生变化：参考结果=" + expected.path(field)
                            + "，本次结果=" + actual.path(field));
        require(expected.path("orderAmount").decimalValue()
                        .compareTo(actual.path("orderAmount").decimalValue()) == 0,
                "重放结果的成交金额发生变化");
    }

    private static void saveOriginalRequests(String mode, String marker,
                                             ObjectNode first, ObjectNode second) throws Exception {
        Path directory = Path.of(System.getProperty("java.io.tmpdir"), "dineflow-i2-http-checks");
        Files.createDirectories(directory);
        Path file = directory.resolve(marker + ".json");
        ObjectNode saved = JSON.createObjectNode().put("mode", mode).put("remark", marker);
        saved.putArray("payloads").add(first).add(second);
        Files.writeString(file, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(saved),
                StandardOpenOption.CREATE_NEW);
        System.out.println("requestId：" + Stream.of(first, second)
                .map(body -> body.path("requestId").asText()).distinct().collect(Collectors.joining(", ")));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private record Reply(int status, JsonNode body, String error) {
        boolean success() { return status >= 200 && status < 300 && body.path("code").asInt(-1) == 1; }
        boolean rejected() { return status >= 200 && status < 300 && body.path("code").asInt(-1) == 0; }
        boolean uncertain() { return !success() && !rejected(); }
        String summary() {
            return "HTTP=" + status + ", code=" + body.path("code")
                    + ", msg=" + body.path("msg").asText(error)
                    + ", orderId=" + body.path("data").path("id").asText("");
        }
    }
}
