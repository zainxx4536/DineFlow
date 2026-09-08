package com.dineflow.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class EmployeeLoginDTO implements Serializable {

    private String username;

    private String password;

}
