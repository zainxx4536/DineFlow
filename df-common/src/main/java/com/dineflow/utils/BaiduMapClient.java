package com.dineflow.utils;

import com.dineflow.exception.OrderBusinessException;
import com.dineflow.model.Coordinate;
import com.dineflow.properties.BaiduMapProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 百度地图 API
 */
@Component
@RequiredArgsConstructor
public class BaiduMapClient {
    // 百度获取经纬度接口
    private static final String GEOCODING_URL = "https://api.map.baidu.com/geocoding/v3/";
    // 百度 RouteMatrix 驾车批量算路接口
    private static final String ROUTE_MATRIX_URL = "https://api.map.baidu.com/routematrix/v2/driving";

    private final BaiduMapProperties baiduMapProperties;

    private final ObjectMapper objectMapper;

    /**
     * 根据地址获取经纬度
     */
    public Coordinate getCoordinate(String address) {

        Map<String, String> params = new HashMap<>();
        params.put("address", address);
        params.put("output", "json");
        params.put("ak", baiduMapProperties.getAk());

        String response = HttpClientUtil.doGet(GEOCODING_URL, params);

        if (response == null || response.isBlank()) {
            throw new OrderBusinessException("地址解析失败");
        }

        try {
            JsonNode root = objectMapper.readTree(response);

            int status = root.path("status").asInt(-1);

            if (status != 0) {
                throw new OrderBusinessException("地址解析失败");
            }

            JsonNode location = root
                    .path("result")
                    .path("location");

            if (location.isMissingNode()) {
                throw new OrderBusinessException("未找到该地址的位置信息");
            }

            return Coordinate.builder()
                    .lng(location.path("lng").asDouble())
                    .lat(location.path("lat").asDouble())
                    .build();

        } catch (JsonProcessingException e) {
            throw new OrderBusinessException("地图服务响应解析失败");
        }
    }

    /**
     * 计算距离店铺的驾车距离，纬度在前，经度在后，也就是 lat,lng。
     * 返回的 distance.value 单位是米。
     *
     * @param destination 终点坐标
     * @return 驾车距离，单位：米
     */
    public double getDrivingDistance(Coordinate destination) {

        Map<String, String> params = new HashMap<>();

        Double shopLng = baiduMapProperties.getShopLng();
        Double shopLat = baiduMapProperties.getShopLat();

        params.put("origins", shopLat + "," + shopLng);
        params.put("destinations", destination.getLat() + "," + destination.getLng());
        params.put("ak", baiduMapProperties.getAk());
        params.put("output", "json");

        String response = HttpClientUtil.doGet(ROUTE_MATRIX_URL, params);

        if (response == null || response.isBlank()) {
            throw new OrderBusinessException("距离计算失败");
        }

        try {
            JsonNode root = objectMapper.readTree(response);

            int status = root.path("status").asInt(-1);

            if (status != 0) {
                throw new OrderBusinessException(
                        "距离计算失败：" + root.path("message").asText()
                );
            }

            JsonNode result = root.path("result");

            if (!result.isArray() || result.isEmpty()) {
                throw new OrderBusinessException("未获取到路线信息");
            }

            JsonNode distance = result.get(0).path("distance");

            double value = distance.path("value").asDouble(-1);

            if (value < 0) {
                throw new OrderBusinessException("距离数据异常");
            }

            return value;

        } catch (JsonProcessingException e) {
            throw new OrderBusinessException("地图服务响应解析失败");
        }
    }
}
