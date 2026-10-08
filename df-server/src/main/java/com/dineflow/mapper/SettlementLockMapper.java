package com.dineflow.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 所有锁必须在 Spring 事务内获得；事务提交或回滚时由 InnoDB 释放。 */
public interface SettlementLockMapper {
    @Select("SELECT id FROM catalog_guard WHERE id = 1 FOR SHARE")
    Long readCatalog();
    @Select("SELECT id FROM catalog_guard WHERE id = 1 FOR UPDATE")
    Long writeCatalog();
    @Select("SELECT id FROM user WHERE id = #{id} FOR UPDATE")
    Long lockUser(@Param("id") Long id);
}
