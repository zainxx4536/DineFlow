package com.dineflow.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.dineflow.utils.ThreadLocalUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 公共字段自动填充处理器
 */
@Component
@Slf4j
public class MyMetaObjectHandler implements MetaObjectHandler {
    /**
     * 新增数据时自动填充
     */
    @Override
    public void insertFill(MetaObject metaObject) {

        log.info("开始进行新增数据公共字段自动填充...");

        LocalDateTime now = LocalDateTime.now();
        Long currentId = ThreadLocalUtil.getCurrentId();

        // 创建时间
        this.strictInsertFill(
                metaObject,
                "createTime",
                LocalDateTime.class,
                now
        );

        // 更新时间
        this.strictInsertFill(
                metaObject,
                "updateTime",
                LocalDateTime.class,
                now
        );

        // 创建人
        this.strictInsertFill(
                metaObject,
                "createUser",
                Long.class,
                currentId
        );

        // 更新人
        this.strictInsertFill(
                metaObject,
                "updateUser",
                Long.class,
                currentId
        );
    }

    /**
     * 修改数据时自动填充
     */
    @Override
    public void updateFill(MetaObject metaObject) {

        log.info("开始进行修改数据公共字段自动填充...");

        LocalDateTime now = LocalDateTime.now();
        Long currentId = ThreadLocalUtil.getCurrentId();

        // 更新时间
        this.strictUpdateFill(
                metaObject,
                "updateTime",
                LocalDateTime.class,
                now
        );

        // 更新人
        this.strictUpdateFill(
                metaObject,
                "updateUser",
                Long.class,
                currentId
        );
    }
}
