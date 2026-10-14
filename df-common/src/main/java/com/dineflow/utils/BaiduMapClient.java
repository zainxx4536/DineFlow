package com.dineflow.utils;

import com.dineflow.exception.OrderBusinessException;
import com.dineflow.model.Coordinate;
import com.dineflow.properties.BaiduMapProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class BaiduMapClient {

    private static final String GEOCODING_URL =
            "https://api.map.baidu.com/geocoding/v3/";

    private static final String ROUTE_MATRIX_URL =
            "https://api.map.baidu.com/routematrix/v2/driving";

    private final BaiduMapProperties baiduMapProperties;
    private final ObjectMapper objectMapper;

    /**
     * 根据地址获取经纬度。
     */
    public Coordinate getCoordinate(String address) {

        if (address == null || address.isBlank()) {
            throw new OrderBusinessException("收货地址不能为空");
        }

        Map<String, String> params = baseParams();
        params.put("address", address);

        JsonNode root = request(GEOCODING_URL, params, "地址解析失败");

        JsonNode location = root.path("result").path("location");

        JsonNode lngNode = location.path("lng");
        JsonNode latNode = location.path("lat");

        // 缺失、null、字符串、布尔值等都不能作为坐标。
        if (!lngNode.isNumber() || !latNode.isNumber()) {
            throw new OrderBusinessException("地图返回的坐标数据异常");
        }

        double lng = lngNode.doubleValue();
        double lat = latNode.doubleValue();

        checkCoordinate(lng, lat, "地图返回的坐标数据异常");

        return Coordinate.builder()
                .lng(lng)
                .lat(lat)
                .build();
    }

    /**
     * 计算店铺到收货地址的驾车距离，单位为米。
     */
    public double getDrivingDistance(Coordinate destination) {

        Double shopLng = baiduMapProperties.getShopLng();
        Double shopLat = baiduMapProperties.getShopLat();

        checkCoordinate(shopLng, shopLat, "店铺坐标配置异常");

        if (destination == null) {
            throw new OrderBusinessException("收货地址坐标不能为空");
        }

        checkCoordinate(destination.getLng(), destination.getLat(), "收货地址坐标异常");

        Map<String, String> params = baseParams();

        // 百度路线接口要求纬度在前、经度在后。
        params.put("origins", shopLat + "," + shopLng);

        params.put("destinations", destination.getLat() + "," + destination.getLng());

        JsonNode root = request(ROUTE_MATRIX_URL, params, "配送距离计算失败");

        JsonNode result = root.path("result");

        // 本次只有一个起点和一个终点，应返回一条结果。
        if (!result.isArray()
                || result.size() != 1
                || !result.get(0).isObject()) {
            throw new OrderBusinessException("地图未返回有效路线");
        }

        JsonNode route = result.get(0);

        JsonNode valueNode = route.path("distance").path("value");

        if (!valueNode.isNumber()) {
            throw new OrderBusinessException("地图返回的距离数据异常");
        }

        double distance = valueNode.doubleValue();

        // NaN、正负无穷和负数均拒绝。
        if (!Double.isFinite(distance) || distance < 0) {
            throw new OrderBusinessException("地图返回的距离数据异常");
        }

        // 官方约定：0也可能表示没有计算结果。
        if (distance == 0) {
            throw new OrderBusinessException("无法确认配送路线，请确认地址后重试");
        }

        return distance;
    }

    /**
     * 构造公共请求参数。
     */
    private Map<String, String> baseParams() {

        String ak = baiduMapProperties.getAk();

        if (ak == null || ak.isBlank()) {
            throw new OrderBusinessException("地图服务尚未配置");
        }

        Map<String, String> params = new HashMap<>();

        params.put("output", "json");
        params.put("ak", ak);

        return params;
    }

    /**
     * 发送HTTP请求，校验公共响应结构和业务状态。
     */
    private JsonNode request(
            String url,
            Map<String, String> params,
            String failureMessage) {

        String response;

        try {
            response = HttpClientUtil.doGet(url, params);
        } catch (RuntimeException e) {
            // 不向调用方返回可能含AK或完整URL的底层异常。
            throw new OrderBusinessException(failureMessage + "，请稍后重试");
        }

        if (response == null || response.isBlank()) {
            throw new OrderBusinessException(failureMessage);
        }

        try {
            // 要求完整响应是一个合法JSON值，不忽略尾部异常内容。
            JsonNode root = objectMapper.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(response);

            if (root == null || !root.isObject()) {
                throw new OrderBusinessException("地图服务响应格式异常");
            }

            JsonNode status = root.path("status");

            // 官方约定status为整数，0表示成功。
            if (!status.isIntegralNumber()
                    || !status.canConvertToInt()
                    || status.intValue() != 0) {
                throw new OrderBusinessException(failureMessage);
            }

            return root;

        } catch (JsonProcessingException e) {
            throw new OrderBusinessException("地图服务响应解析失败");
        }
    }

    /**
     * 检查坐标是否存在、有限且处于合法范围。
     */
    private void checkCoordinate(
            Double lng,
            Double lat,
            String message) {

        if (lng == null
                || lat == null
                || !Double.isFinite(lng)
                || !Double.isFinite(lat)
                || lng < -180
                || lng > 180
                || lat < -90
                || lat > 90) {
            throw new OrderBusinessException(message);
        }
    }
}