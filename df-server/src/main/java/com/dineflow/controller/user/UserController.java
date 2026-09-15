package com.dineflow.controller.user;

import com.dineflow.dto.UserLoginDTO;
import com.dineflow.result.Result;
import com.dineflow.service.IUserService;
import com.dineflow.vo.UserLoginVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user/user")
@Slf4j
@RequiredArgsConstructor
@Api(tags = "C端-用户相关接口")
public class UserController {

    private final IUserService userService;

    @PostMapping("/login")
    @ApiOperation("用户登录接口")
    public Result<UserLoginVO> userLogin(@RequestBody UserLoginDTO userLoginDTO) {
        log.info("用户登录：{}", userLoginDTO);
        UserLoginVO userLoginVO = userService.userLogin(userLoginDTO);
        return Result.success(userLoginVO);
    }
}
