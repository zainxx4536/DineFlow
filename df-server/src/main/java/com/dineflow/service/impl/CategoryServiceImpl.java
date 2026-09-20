package com.dineflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.dineflow.constant.MessageConstant;
import com.dineflow.constant.RedisKeyConstant;
import com.dineflow.constant.StatusConstant;
import com.dineflow.dto.CategoryDTO;
import com.dineflow.dto.CategoryPageQueryDTO;
import com.dineflow.entity.Category;
import com.dineflow.entity.Dish;
import com.dineflow.entity.Setmeal;
import com.dineflow.exception.DeletionNotAllowedException;
import com.dineflow.mapper.CategoryMapper;
import com.dineflow.result.PageResult;
import com.dineflow.service.ICategoryService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
public class CategoryServiceImpl
        extends ServiceImpl<CategoryMapper, Category>
        implements ICategoryService {

    /**
     * 新增分类
     */
    @Override
    @CacheEvict(cacheNames = RedisKeyConstant.CATEGORY_LIST, allEntries = true)
    public void addNewCategory(CategoryDTO categoryDTO) {

        Category category = BeanUtil.copyProperties(categoryDTO, Category.class);

        // 新增分类默认启用
        category.setStatus(StatusConstant.ENABLE);

        save(category);
    }

    /**
     * 分类分页查询
     */
    @Override
    public PageResult<Category> categoryPageQuery(CategoryPageQueryDTO categoryPageQueryDTO) {

        // 1. 准备分页数据
        int pageNo = categoryPageQueryDTO.getPage();
        int pageSize = categoryPageQueryDTO.getPageSize();

        Page<Category> page = Page.of(pageNo, pageSize);

        // 分类优先按照 sort 排序，同 sort 时按照 id 排序
        page.addOrder(new OrderItem().setColumn("sort").setAsc(true));
        page.addOrder(new OrderItem().setColumn("id").setAsc(true));

        // 2. 构造查询条件
        LambdaQueryWrapper<Category> wrapper =
                new LambdaQueryWrapper<Category>()
                        .like(StrUtil.isNotBlank(categoryPageQueryDTO.getName()),
                                Category::getName,
                                categoryPageQueryDTO.getName()
                        )
                        .eq(categoryPageQueryDTO.getType() != null,
                                Category::getType,
                                categoryPageQueryDTO.getType()
                        );

        // 3. 条件分页查询
        Page<Category> resultPage = page(page, wrapper);

        return new PageResult<>(resultPage.getTotal(), resultPage.getRecords());
    }

    /**
     * 根据 ID 删除分类
     */
    @Override
    @CacheEvict(cacheNames = RedisKeyConstant.CATEGORY_LIST, allEntries = true)
    public void delCategoryById(Long id) {

        // 1. 判断该分类是否关联菜品
        boolean dishExists = Db.lambdaQuery(Dish.class)
                .eq(Dish::getCategoryId, id)
                .exists();

        if (dishExists) {
            throw new DeletionNotAllowedException(MessageConstant.CATEGORY_BE_RELATED_BY_DISH);
        }

        // 2. 判断该分类是否关联合集套餐
        boolean setmealExists = Db.lambdaQuery(Setmeal.class)
                .eq(Setmeal::getCategoryId, id)
                .exists();

        if (setmealExists) {
            throw new DeletionNotAllowedException(MessageConstant.CATEGORY_BE_RELATED_BY_SETMEAL);
        }

        // 3. 删除分类
        removeById(id);
    }

    /**
     * 修改分类
     */
    @Override
    @CacheEvict(cacheNames = RedisKeyConstant.CATEGORY_LIST, allEntries = true)
    public void modifyCategory(CategoryDTO categoryDTO) {

        Category category = BeanUtil.copyProperties(categoryDTO, Category.class);

        updateById(category);
    }

    /**
     * 修改分类状态
     */
    @Override
    @CacheEvict(cacheNames = RedisKeyConstant.CATEGORY_LIST, allEntries = true)
    public void modifyCategoryStatus(Long id, Integer status) {

        Category category = Category.builder()
                .id(id)
                .status(status)
                .build();

        updateById(category);
    }

    /**
     * 管理端-根据类型查询分类
     */
    @Override
    public List<Category> getCategoryByType(Integer type) {

        return lambdaQuery()
                .eq(type != null,
                        Category::getType,
                        type
                )
                .orderByAsc(Category::getSort)
                .orderByAsc(Category::getId)
                .list();
    }

    /**
     * C端-根据类型查询分类
     */
    @Override
    @Cacheable(cacheNames = RedisKeyConstant.CATEGORY_LIST, key = "#type == null ? 'all' : #type")
    public List<Category> categoryQueryByType(Integer type) {

        return lambdaQuery()
                // 根据类型筛选
                .eq(type != null,
                        Category::getType,
                        type
                )
                // C端只展示启用分类
                .eq(Category::getStatus,
                        StatusConstant.ENABLE
                )
                // 按分类排序字段排序
                .orderByAsc(Category::getSort)
                .orderByAsc(Category::getId)
                .list();
    }
}