package com.dineflow.service;

import com.dineflow.vo.BusinessDataVO;
import com.dineflow.vo.DishOverViewVO;
import com.dineflow.vo.OrderOverViewVO;
import com.dineflow.vo.SetmealOverViewVO;

import java.time.LocalDateTime;

public interface WorkspaceService {

    BusinessDataVO getBusinessData(LocalDateTime beginTime, LocalDateTime endTime);

    OrderOverViewVO getOrderOverView();

    DishOverViewVO getDishOverView();

    SetmealOverViewVO getSetmealOverView();
}
