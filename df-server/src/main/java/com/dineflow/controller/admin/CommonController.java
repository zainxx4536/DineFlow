package com.dineflow.controller.admin;

import com.dineflow.result.Result;
import com.dineflow.utils.AliOssUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/admin/common")
@Slf4j
@RequiredArgsConstructor
@Api(tags = "通用接口")
public class CommonController {

    private final AliOssUtil aliOssUtil;

    @PostMapping("/upload")
    @ApiOperation("文件上传")
    public Result<String> fileUpload(@RequestParam("file") MultipartFile file) {
        log.info("文件上传：{}", file == null ? null : file.getOriginalFilename());
        String url = aliOssUtil.upload(file);
        return Result.success(url);
    }
}
