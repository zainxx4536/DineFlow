package com.dineflow.utils;

import com.aliyun.sdk.service.oss2.OSSClient;
import com.aliyun.sdk.service.oss2.OSSClientBuilder;
import com.aliyun.sdk.service.oss2.credentials.CredentialsProvider;
import com.aliyun.sdk.service.oss2.credentials.EnvironmentVariableCredentialsProvider;
import com.aliyun.sdk.service.oss2.models.PutObjectRequest;
import com.aliyun.sdk.service.oss2.models.PutObjectResult;
import com.aliyun.sdk.service.oss2.transport.BinaryData;
import com.dineflow.exception.UploadFileFailedException;
import com.dineflow.exception.UploadFileIsNullException;
import com.dineflow.properties.AliOssProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

/**
 * 阿里云 OSS 文件上传工具类
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AliOssUtil {

    private final AliOssProperties aliOssProperties;

    /**
     * 上传文件到 OSS
     *
     * @param file 上传文件
     * @return 文件访问地址
     */
    public String upload(MultipartFile file) {

        // 校验文件
        if (file == null || file.isEmpty()) {
            throw new UploadFileIsNullException("上传文件为空");
        }

        String originalFilename = file.getOriginalFilename();

        // 获取文件后缀
        String suffix = getFileSuffix(originalFilename);

        // 生成 OSS 对象名称
        String objectName =
                "images/"
                        + UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        + suffix;

        // 从环境变量读取 AccessKey
        CredentialsProvider credentialsProvider =
                new EnvironmentVariableCredentialsProvider();

        // 创建 OSS Client
        OSSClientBuilder clientBuilder =
                OSSClient.newBuilder()
                        .credentialsProvider(credentialsProvider)
                        .region(aliOssProperties.getRegion());

        String endpoint = aliOssProperties.getEndpoint();

        if (endpoint == null || endpoint.isEmpty()) {
            throw new IllegalStateException("OSS endpoint 未配置");
        }

        clientBuilder.endpoint(endpoint);

        // 上传文件
        try (OSSClient client = clientBuilder.build();
             InputStream inputStream = file.getInputStream()) {

            PutObjectRequest request =
                    PutObjectRequest.newBuilder()
                            .bucket(aliOssProperties.getBucketName())
                            .key(objectName)
                            .body(BinaryData.fromStream(inputStream))
                            .build();

            PutObjectResult result =
                    client.putObject(request);

            log.info(
                    "OSS 文件上传成功，objectName：{}，requestId：{}",
                    objectName,
                    result.requestId()
            );

            return buildFileUrl(objectName);

        } catch (Exception e) {
            log.error(
                    "OSS 文件上传失败，文件名：{}",
                    originalFilename,
                    e
            );
            throw new UploadFileFailedException("文件上传失败");
        }
    }

    /**
     * 获取文件后缀
     * <p>
     * abc.jpg -> .jpg
     */
    private String getFileSuffix(String filename) {

        if (filename == null || filename.isEmpty()) {
            return "";
        }

        int index = filename.lastIndexOf('.');

        if (index == -1) {
            return "";
        }

        return filename.substring(index);
    }

    /**
     * 构造 OSS 默认访问地址
     * OSS 上传文件后，对象按固定规则拥有可访问地址，
     * 但上传接口通常不直接返回 URL，
     * 需要后端根据 Bucket + Endpoint + ObjectName 构建后返回给前端。
     * 前提是将 Bucket 设置为公共读写，如果 Bucket 是私有读，
     * 这个“默认 URL”虽然能定位到对象，但不能匿名直接访问，需要额外生成带签名的临时 URL。
     */
    private String buildFileUrl(String objectName) {

        String endpoint =
                aliOssProperties.getEndpoint();

        String endpointHost =
                endpoint
                        .replace("https://", "")
                        .replace("http://", "");

        return "https://"
                + aliOssProperties.getBucketName()
                + "."
                + endpointHost
                + "/"
                + objectName;
    }
}