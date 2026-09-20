package com.dineflow.utils;

/**
 * ThreadLocal 工具类，存储当前操作员工、用户的ID
 */
public class ThreadLocalUtil {

    public static ThreadLocal<Long> threadLocal = new ThreadLocal<>();

    public static void setCurrentId(Long id) {
        threadLocal.set(id);
    }

    public static Long getCurrentId() {
        return threadLocal.get();
    }

    public static void removeCurrentId() {
        threadLocal.remove();
    }

}
