package com.dineflow.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.dineflow.dto.CategoryDTO;
import com.dineflow.dto.CategoryPageQueryDTO;
import com.dineflow.entity.Category;
import com.dineflow.result.PageResult;

import java.util.List;

/**
 * <p>
 * 菜品及套餐分类 服务类
 * </p>
 *
 * @author zainxx
 * @since 2026-09-08
 */
public interface ICategoryService extends IService<Category> {

    void addNewCategory(CategoryDTO categoryDTO);

    PageResult<Category> categoryPageQuery(CategoryPageQueryDTO categoryPageQueryDTO);

    void delCategoryById(Long id);

    void modifyCategory(CategoryDTO categoryDTO);

    void modifyCategoryStatus(Long id, Integer status);

    List<Category> getCategoryByType(Integer type);
}
