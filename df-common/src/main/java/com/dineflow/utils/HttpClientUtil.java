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

    private static final int TIMEOUT_MSEC = 5 * 1000;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 发送 GET 请求
     */
    public static String doGet(String url, Map<String, String> paramMap) {

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {

            URIBuilder builder = new URIBuilder(url);

            if (paramMap != null) {
                for (Map.Entry<String, String> entry : paramMap.entrySet()) {
                    builder.addParameter(entry.getKey(), entry.getValue());
                }
            }

            URI uri = builder.build();

            HttpGet httpGet = new HttpGet(uri);
            httpGet.setConfig(builderRequestConfig());

            try (CloseableHttpResponse response = httpClient.execute(httpGet)) {

                int statusCode = response.getStatusLine().getStatusCode();

                if (statusCode >= 200 && statusCode < 300) {
                    return EntityUtils.toString(
                            response.getEntity(),
                            StandardCharsets.UTF_8
                    );
                }

                return "";
            }

        } catch (Exception e) {
            throw new RuntimeException("GET 请求失败", e);
        }
    }

    /**
     * 发送 POST 表单请求
     */
    public static String doPost(String url, Map<String, String> paramMap) {

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {

            HttpPost httpPost = new HttpPost(url);

            if (paramMap != null && !paramMap.isEmpty()) {

                List<NameValuePair> paramList = new ArrayList<>();

                for (Map.Entry<String, String> param : paramMap.entrySet()) {
                    paramList.add(
                            new BasicNameValuePair(
                                    param.getKey(),
                                    param.getValue()
                            )
                    );
                }

                UrlEncodedFormEntity entity =
                        new UrlEncodedFormEntity(
                                paramList,
                                StandardCharsets.UTF_8
                        );

                httpPost.setEntity(entity);
            }

            httpPost.setConfig(builderRequestConfig());

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {

                return EntityUtils.toString(
                        response.getEntity(),
                        StandardCharsets.UTF_8
                );
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

                String json =
                        OBJECT_MAPPER.writeValueAsString(paramMap);

                StringEntity entity = new StringEntity(
                        json,
                        ContentType.APPLICATION_JSON
                                .withCharset(StandardCharsets.UTF_8)
                );

                httpPost.setEntity(entity);
            }

            httpPost.setConfig(builderRequestConfig());

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {

                return EntityUtils.toString(
                        response.getEntity(),
                        StandardCharsets.UTF_8
                );
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
                .setConnectTimeout(TIMEOUT_MSEC)
                .setConnectionRequestTimeout(TIMEOUT_MSEC)
                .setSocketTimeout(TIMEOUT_MSEC)
                .build();
    }
}