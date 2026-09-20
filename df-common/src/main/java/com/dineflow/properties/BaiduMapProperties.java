package com.dineflow.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "dineflow.baidu-map")
@Data
public class BaiduMapProperties {
    /**
     * 百度地图 ak
     */
    private String ak;

    /**
     * 店铺经度
     */
    private Double shopLng;

    /**
     * 店铺纬度
     */
    private Double shopLat;

}
