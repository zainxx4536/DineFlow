package com.dineflow.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Coordinate {

    /**
     * 经度
     */
    private Double lng;

    /**
     * 纬度
     */
    private Double lat;
}
