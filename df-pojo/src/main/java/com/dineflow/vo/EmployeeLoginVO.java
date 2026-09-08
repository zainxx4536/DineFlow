package com.dineflow.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeLoginVO implements Serializable {

    private Long id;

    //用户名
    private String userName;

    //姓名
    private String name;

    //token
    private String token;

}
