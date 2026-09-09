package com.dineflow;

import com.dineflow.entity.Employee;
import com.dineflow.service.IEmployeeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
class DfServerApplicationTests {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private IEmployeeService employeeService;

    @Test
    void contextLoads() {
    }

    @Test
    void testBCryptEncoder() {
        String encode = passwordEncoder.encode("123456");
        System.out.println(encode);
    }

    @Test
    void testBCryptMatches() {
        Employee employee = employeeService.getById("1");
        boolean matches = passwordEncoder.matches("123456", employee.getPassword());
        System.out.println(matches);
    }
}
