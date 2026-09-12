package com.dineflow.dto;

import com.dineflow.entity.DishFlavor;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@ApiModel(description = "新增菜品时传递的数据模型")
public class DishDTO implements Serializable {
    @ApiModelProperty("主键")
    private Long id;

    @ApiModelProperty("菜品名称")
    private String name;

    @ApiModelProperty("菜品分类ID")
    private Long categoryId;

    @ApiModelProperty("菜品价格")
    private BigDecimal price;

    @ApiModelProperty("菜品图片")
    private String image;

    @ApiModelProperty("菜品描述信息")
    private String description;

    @ApiModelProperty("菜品状态：0停售;1起售")
    private Integer status;

    @ApiModelProperty("菜品口味")
    private List<DishFlavor> flavors = new ArrayList<>();
}
