package com.dineflow.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "dineflow.alioss")
@Data
public class AliOssProperties {

    private String endpoint;
    private String bucketName;
    private String region;

}
