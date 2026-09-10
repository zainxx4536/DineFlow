package com.dineflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.dineflow.constant.StatusConstant;
import com.dineflow.dto.CategoryDTO;
import com.dineflow.dto.CategoryPageQueryDTO;
import com.dineflow.entity.Category;
import com.dineflow.mapper.CategoryMapper;
import com.dineflow.result.PageResult;
import com.dineflow.service.ICategoryService;
import com.dineflow.utils.ThreadLocalUtil;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 菜品及套餐分类 服务实现类
 * </p>
 *
 * @author zainxx
 * @since 2026-09-08
 */
@Service
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements ICategoryService {

    /**
     * 新增分类
     */
    @Override
    public void addNewCategory(CategoryDTO categoryDTO) {
        Category category = BeanUtil.copyProperties(categoryDTO, Category.class);
        // TODO 使用自动填充替换
        category.setStatus(StatusConstant.ENABLE);
        category.setCreateTime(LocalDateTime.now());
        category.setUpdateTime(LocalDateTime.now());
        Long empId = ThreadLocalUtil.getCurrentId();
        category.setCreateUser(empId);
        category.setUpdateUser(empId);

        save(category);
    }

    /**
     * 分类分页查询
     */
    @Override
    public PageResult<Category> categoryPageQuery(CategoryPageQueryDTO categoryPageQueryDTO) {
        //准备分页数据
        int pageNo = categoryPageQueryDTO.getPage();
        int pageSize = categoryPageQueryDTO.getPageSize();
        Page<Category> page = Page.of(pageNo, pageSize);
        page.addOrder(new OrderItem().setColumn("sort").setAsc(true));
        page.addOrder(new OrderItem().setColumn("id").setAsc(true));
        //请求中可能有name、type作为查询条件
        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<Category>()
                .like(
                        StrUtil.isNotBlank(categoryPageQueryDTO.getName()),
                        Category::getName,
                        categoryPageQueryDTO.getName()
                ).eq(
                        categoryPageQueryDTO.getType() != null,
                        Category::getType,
                        categoryPageQueryDTO.getType()
                );
        //进行条件分页查询
        Page<Category> p = page(page, wrapper);
        return new PageResult<>(p.getTotal(), p.getRecords());
    }

    /**
     * 根据 ID 删除分类
     */
    @Override
    public void delCategoryById(Long id) {
        removeById(id);
    }

    /**
     * 修改分类
     */
    @Override
    public void modifyCategory(CategoryDTO categoryDTO) {
        Category category = BeanUtil.copyProperties(categoryDTO, Category.class);
        category.setUpdateTime(LocalDateTime.now());
        Long empId = ThreadLocalUtil.getCurrentId();
        category.setUpdateUser(empId);
        updateById(category);
    }

    /**
     * 修改分类状态
     */
    @Override
    public void modifyCategoryStatus(Long id, Integer status) {
        lambdaUpdate()
                .set(Category::getStatus, status)
                .eq(Category::getId, id)
                .update();
    }

    /**
     * 根据类型查询分类
     */
    @Override
    public List<Category> getCategoryByType(Integer type) {
        return lambdaQuery()
                .eq(Category::getType, type)
                .list();
    }
}
