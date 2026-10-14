package com.dineflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class OrderSubmitConcurrencyCheck {

    private static final String BASE_URL = "http://127.0.0.1:8080/user";

    private static final ObjectMapper JSON = new ObjectMapper();

    public static void main(String[] args) throws Exception {

        if (args.length != 1) {
            throw new IllegalArgumentException("运行参数必须填写本人地址ID");
        }

        String token = System.getenv("DINEFLOW_TEST_TOKEN");

        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("请设置环境变量DINEFLOW_TEST_TOKEN");
        }

        long addressId = Long.parseLong(args[0]);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        // 读取本人地址及对应版本。
        JsonNode address = data(client.send(
                request(
                        "/addressBook/" + addressId,
                        "GET",
                        null,
                        token),
                HttpResponse.BodyHandlers.ofString()));

        // 读取当前整车报价，作为两次提交共同使用的确认数据。
        JsonNode quote = data(client.send(
                request(
                        "/order/preview",
                        "POST",
                        "{}",
                        token),
                HttpResponse.BodyHandlers.ofString()));

        if (address.get("version") == null
                || address.get("version").isNull()) {
            throw new IllegalStateException("地址未返回版本");
        }

        String marker = "parallel-" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);

        ObjectNode body = JSON.createObjectNode();

        body.put("addressBookId", addressId);
        body.set("addressVersion", address.get("version"));
        body.set("amount", quote.get("payableAmount"));

        body.put("payMethod", 1);
        body.put("deliveryStatus", 1);
        body.put("tablewareStatus", 1);
        body.set("tablewareNumber", quote.get("totalNumber"));
        body.put("remark", marker);

        ArrayNode items = body.putArray("items");

        for (JsonNode row : quote.path("items")) {
            ObjectNode item = items.addObject();

            for (String field : List.of(
                    "cartId",
                    "cartVersion",
                    "dishId",
                    "setmealId",
                    "dishFlavor",
                    "number",
                    "currentPrice")) {

                item.set(field, row.get(field));
            }
        }

        // 先输出标记，网络结果不明确时也能按此标记查单。
        System.out.println("数据库检查用remark：" + marker);

        // 两次发送同一份已冻结的确认参数。
        HttpRequest submit = request(
                "/order/submit",
                "POST",
                JSON.writeValueAsString(body),
                token);

        var first = client.sendAsync(submit, HttpResponse.BodyHandlers.ofString());

        var second = client.sendAsync(submit, HttpResponse.BodyHandlers.ofString());

        CompletableFuture.allOf(first, second).join();

        int successes = 0;

        for (var future : List.of(first, second)) {

            HttpResponse<String> response = future.join();
            JsonNode result = JSON.readTree(response.body());

            boolean success = response.statusCode() >= 200
                    && response.statusCode() < 300
                    && result.path("code").asInt(-1) == 1;

            if (success) {
                successes++;
            }

            System.out.printf(
                    "HTTP=%d, code=%s, msg=%s, orderId=%s%n",
                    response.statusCode(),
                    result.path("code"),
                    result.path("msg"),
                    result.path("data").path("id"));
        }

        System.out.println("成功请求数：" + successes);

        if (successes != 1) {
            throw new AssertionError("稳定测试条件下应当恰好一个请求成功，请检查失败原因");
        }
    }

    private static HttpRequest request(
            String path,
            String method,
            String body,
            String token) {

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(BASE_URL + path))
                .timeout(Duration.ofSeconds(40))
                .header("authentication", token)
                .header("Content-Type", "application/json");

        return body == null
                ? builder.GET().build()
                : builder.method(method, HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    private static JsonNode data(HttpResponse<String> response) throws Exception {

        JsonNode root = JSON.readTree(response.body());

        if (response.statusCode() < 200
                || response.statusCode() >= 300
                || root.path("code").asInt(-1) != 1
                || root.path("data").isNull()
                || root.path("data").isMissingNode()) {

            throw new IllegalStateException("准备测试数据失败：" + root.path("msg"));
        }

        return root.get("data");
    }
}