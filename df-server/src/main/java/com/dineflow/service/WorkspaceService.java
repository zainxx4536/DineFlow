package com.dineflow.service;

import com.dineflow.vo.BusinessDataVO;
import com.dineflow.vo.DishOverViewVO;
import com.dineflow.vo.OrderOverViewVO;
import com.dineflow.vo.SetmealOverViewVO;

public interface WorkspaceService {

    BusinessDataVO getBusinessData();

    OrderOverViewVO getOrderOverView();

    DishOverViewVO getDishOverView();

    SetmealOverViewVO getSetmealOverView();
}
