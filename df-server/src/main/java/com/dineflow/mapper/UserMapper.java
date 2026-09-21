package com.dineflow.mapper;

import com.dineflow.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dineflow.vo.UserDailyVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 用户信息 Mapper 接口
 * </p>
 *
 * @author zainxx
 * @since 2026-09-08
 */
public interface UserMapper extends BaseMapper<User> {

    List<UserDailyVO> newUserStatistics(LocalDateTime beginTime, LocalDateTime endTime);
}
