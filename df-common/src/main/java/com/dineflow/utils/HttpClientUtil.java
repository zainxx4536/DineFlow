package com.dineflow.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * HttpClient 工具类
 */
public class HttpClientUtil {
    //请求超时时间
    private static final int TIMEOUT_MSES = 5 * 1000;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 发送 GET 请求，GET 请求的参数直接拼接在 URL 后面
     */
    public static String doGet(
            String url,
            Map<String, String> paramMap) {

        //try-with-resources 执行完会自动关闭资源
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            //构造包含请求参数的完整请求的 URI
            URIBuilder builder = new URIBuilder(url);
            if (paramMap != null) {
                for (Map.Entry<String, String> entry : paramMap.entrySet()) {
                    builder.addParameter(entry.getKey(), entry.getValue());
                }
            }
            URI uri = builder.build();

            //创建并发送 GET 请求
            HttpGet httpGet = new HttpGet(uri);
            //给这个请求设置连接超时、等待连接超时、读取数据超时
            httpGet.setConfig(builderRequestConfig());
            try (CloseableHttpResponse response = httpClient.execute(httpGet)) {

                int statusCode = response.getStatusLine().getStatusCode();
                //请求成功时读取响应内容
                if (statusCode >= 200 && statusCode < 300) {
                    return EntityUtils.toString(
                            response.getEntity(),
                            StandardCharsets.UTF_8
                    );
                    //到这里返回的还是 JSON 字符串，还需要 Jackson 再解析
                }
                return "";
            }
        } catch (Exception e) {
            throw new RuntimeException("GET 请求失败", e);
        }
    }

    /**
     * 发送 POST 请求，POST 请求的参数放在请求体中
     */
    public static String doPost(
            String url,
            Map<String, String> paramMap) {
        //try-with-resources 执行完会自动关闭资源
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            //创建 POST 请求
            HttpPost httpPost = new HttpPost(url);

            if (paramMap != null && !paramMap.isEmpty()) {
                //把 MAP 装换成 List
                List<NameValuePair> paramList = new ArrayList<>();

                for (Map.Entry<String, String> param : paramMap.entrySet()) {
                    paramList.add(
                            //创建一个“参数名 = 参数值”的 NameValuePair
                            new BasicNameValuePair(
                                    param.getKey(),
                                    param.getValue()
                            )
                    );
                }
                //把参数编码成表单格式：username=admin&password=123456
                UrlEncodedFormEntity entity =
                        new UrlEncodedFormEntity(
                                paramList,
                                StandardCharsets.UTF_8
                        );
                //把数据放进 POST 请求体
                httpPost.setEntity(entity);
            }
            //给这个请求设置连接超时、等待连接超时、读取数据超时
            httpPost.setConfig(builderRequestConfig());

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                int statusCode = response.getStatusLine().getStatusCode();
                //请求成功时读取响应内容
                if (statusCode >= 200 && statusCode < 300) {
                    return EntityUtils.toString(
                            response.getEntity(),
                            StandardCharsets.UTF_8
                    );
                    //到这里返回的还是 JSON 字符串，还需要 Jackson 再解析
                }
                return "";
            }
        } catch (Exception e) {
            throw new RuntimeException("POST 请求失败", e);
        }
    }

    /**
     * 发送 POST JSON 请求
     */
    public static String doPost4Json(
            String url,
            Map<String, String> paramMap) {

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {

            HttpPost httpPost = new HttpPost(url);

            if (paramMap != null && !paramMap.isEmpty()) {

                String json = OBJECT_MAPPER.writeValueAsString(paramMap);

                StringEntity entity = new StringEntity(
                        json,
                        ContentType.APPLICATION_JSON
                                .withCharset(StandardCharsets.UTF_8)
                );

                httpPost.setEntity(entity);
            }

            httpPost.setConfig(builderRequestConfig());

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                int statusCode = response.getStatusLine().getStatusCode();
                //请求成功时读取响应内容
                if (statusCode >= 200 && statusCode < 300) {
                    return EntityUtils.toString(
                            response.getEntity(),
                            StandardCharsets.UTF_8
                    );
                    //到这里返回的还是 JSON 字符串，还需要 Jackson 再解析
                }
                return "";
            }
        } catch (Exception e) {
            throw new RuntimeException("POST JSON 请求失败", e);
        }
    }

    /**
     * 构建请求超时配置
     */
    private static RequestConfig builderRequestConfig() {
        return RequestConfig.custom()
                .setConnectTimeout(TIMEOUT_MSES)
                .setConnectionRequestTimeout(TIMEOUT_MSES)
                .setSocketTimeout(TIMEOUT_MSES)
                .build();
    }
}