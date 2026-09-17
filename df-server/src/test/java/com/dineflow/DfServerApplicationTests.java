package com.dineflow;

import cn.hutool.core.lang.UUID;
import cn.hutool.json.JSONObject;
import com.dineflow.entity.Employee;
import com.dineflow.service.IEmployeeService;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.io.IOException;
import java.time.LocalDateTime;

@SpringBootTest
@EnableTransactionManagement
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

    @Test
    void testUUID() {
        System.out.println(UUID.randomUUID().toString(true));
        String orderNumber = UUID.randomUUID().toString(true) + LocalDateTime.now().toString();
        System.out.println(orderNumber);
    }

    @Test
    void testHttpClientGet() throws IOException {
        //1. 创建HttpClient对象
        CloseableHttpClient httpClient = HttpClients.createDefault();
        //2. 创建请求对象
        HttpGet httpGet = new HttpGet("http://localhost:8080/user/shop/status");
        //3. 发送请求，接收响应结果
        CloseableHttpResponse response = httpClient.execute(httpGet);
        //4. 解析响应结果
        int statusCode = response.getStatusLine().getStatusCode();
        System.out.println("服务端返回的状态码为：" + statusCode);
        HttpEntity entity = response.getEntity();
        String body = EntityUtils.toString(entity);
        System.out.println("服务端返回的数据为：" + body);
        //5. 关闭资源
        response.close();
        httpClient.close();
    }

    @Test
    void testHttpClientPost() throws IOException {
        //1. 创建HttpClient对象
        CloseableHttpClient httpClient = HttpClients.createDefault();
        //2. 创建请求对象,并设置请求参数实体
        HttpPost httpPost = new HttpPost("http://localhost:8080/admin/employee/login");

        JSONObject jsonObject = new JSONObject();
        jsonObject.set("username", "admin");
        jsonObject.set("password", "123456");
        StringEntity entity = new StringEntity(jsonObject.toString());
        //指定请求编码方式
        entity.setContentEncoding("utf-8");
        //指定数据格式
        entity.setContentType("application/json");
        httpPost.setEntity(entity);
        //3. 发送请求，接收响应结果
        CloseableHttpResponse response = httpClient.execute(httpPost);
        //4. 解析响应结果
        int statusCode = response.getStatusLine().getStatusCode();
        System.out.println("服务端返回的状态码为：" + statusCode);

        HttpEntity responseEntity = response.getEntity();
        String body = EntityUtils.toString(responseEntity);
        System.out.println("服务端返回的数据为：" + body);
        //5. 关闭资源
        response.close();
        httpClient.close();
    }
}
