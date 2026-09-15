package com.dineflow.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.dineflow.dto.UserLoginDTO;
import com.dineflow.entity.User;
import com.dineflow.vo.UserLoginVO;

/**
 * <p>
 * 用户信息 服务类
 * </p>
 *
 * @author zainxx
 * @since 2026-09-08
 */
public interface IUserService extends IService<User> {

    UserLoginVO userLogin(UserLoginDTO userLoginDTO);
}
