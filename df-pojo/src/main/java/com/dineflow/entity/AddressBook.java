package com.dineflow.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 地址簿
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("address_book")
public class AddressBook implements Serializable {

    private static final long serialVersionUID = 1L;
    @TableId("id")
    private Long id;

    //用户id
    @TableField("user_id")
    private Long userId;

    //收货人
    @TableField("consignee")
    private String consignee;

    //手机号
    @TableField("phone")
    private String phone;

    //性别 0 女 1 男
    @TableField("sex")
    private String sex;

    //省级区划编号
    @TableField("province_code")
    private String provinceCode;

    //省级名称
    @TableField("province_name")
    private String provinceName;

    //市级区划编号
    @TableField("city_code")
    private String cityCode;

    //市级名称
    @TableField("city_name")
    private String cityName;

    //区级区划编号
    @TableField("district_code")
    private String districtCode;

    //区级名称
    @TableField("district_name")
    private String districtName;

    //详细地址
    @TableField("detail")
    private String detail;

    //标签
    @TableField("label")
    private String label;

    //是否默认 0否 1是
    @TableField("is_default")
    private Integer isDefault;
}
