package com.dineflow.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class AddressBookAddDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String consignee;

    private String phone;

    // 0：女；1：男。
    private String sex;

    private String provinceCode;

    private String provinceName;

    private String cityCode;

    private String cityName;

    private String districtCode;

    private String districtName;

    private String detail;

    private String label;
}