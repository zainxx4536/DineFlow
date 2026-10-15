package com.dineflow;

import com.dineflow.entity.ShoppingCart;
import com.dineflow.utils.CartItemKeyUtil;

import java.sql.*;
import java.util.*;

public class CartItemKeyBackfillTool {

    public static void main(String[] args) throws Exception {
        String url = requiredEnv("DINEFLOW_DB_URL");
        String user = requiredEnv("DINEFLOW_DB_USER");
        String password = System.getenv("DINEFLOW_DB_PASSWORD");

        if (password == null) {
            throw new IllegalArgumentException(
                    "请设置DINEFLOW_DB_PASSWORD，可为空字符串");
        }

        try (Connection connection =
                     DriverManager.getConnection(url, user, password)) {

            connection.setAutoCommit(false);

            try {
                List<ShoppingCart> rows = new ArrayList<>();
                Map<String, Long> identities = new HashMap<>();

                String query =
                        "SELECT id,user_id,dish_id,setmeal_id,dish_flavor,"
                                + "number,version,item_key "
                                + "FROM shopping_cart ORDER BY id FOR UPDATE";

                // 先检查所有旧数据，避免遇到重复记录后才发现问题。
                try (PreparedStatement statement =
                             connection.prepareStatement(query);
                     ResultSet rs = statement.executeQuery()) {

                    while (rs.next()) {
                        ShoppingCart row = new ShoppingCart();

                        row.setId(rs.getLong("id"));
                        row.setUserId(rs.getLong("user_id"));
                        row.setDishId(rs.getObject("dish_id", Long.class));
                        row.setSetmealId(rs.getObject("setmeal_id", Long.class));
                        row.setDishFlavor(rs.getString("dish_flavor"));
                        row.setNumber(rs.getInt("number"));
                        row.setVersion(rs.getObject("version", Integer.class));
                        row.setItemKey(rs.getString("item_key"));

                        if (row.getUserId() <= 0
                                || row.getNumber() < 1
                                || row.getNumber() > 99
                                || row.getVersion() == null
                                || row.getVersion() < 0
                                || row.getVersion() == Integer.MAX_VALUE) {
                            throw new IllegalStateException(
                                    "请先整理异常购物车行，id=" + row.getId());
                        }

                        String key = CartItemKeyUtil.build(
                                row.getDishId(),
                                row.getSetmealId(),
                                row.getDishFlavor()
                        );

                        String identity = row.getUserId() + ":" + key;

                        Long previous = identities.putIfAbsent(
                                identity, row.getId());

                        if (previous != null) {
                            throw new IllegalStateException(
                                    "发现重复同款行，id="
                                            + previous + "、" + row.getId()
                                            + "。请先核对并整理，"
                                            + "本工具不会自动删除或合并");
                        }

                        rows.add(row);
                    }
                }

                int changed = 0;

                String update =
                        "UPDATE shopping_cart "
                                + "SET dish_flavor=?,item_key=?,version=version+1 "
                                + "WHERE id=? AND user_id=? AND version=?";

                try (PreparedStatement statement =
                             connection.prepareStatement(update)) {

                    for (ShoppingCart row : rows) {
                        String flavor = CartItemKeyUtil.normalizeFlavor(
                                row.getDishFlavor());

                        String key = CartItemKeyUtil.build(
                                row.getDishId(),
                                row.getSetmealId(),
                                flavor
                        );

                        // 已经正确回填的记录不重复更新。
                        if (Objects.equals(flavor, row.getDishFlavor())
                                && key.equals(row.getItemKey())) {
                            continue;
                        }

                        statement.setString(1, flavor);
                        statement.setString(2, key);
                        statement.setLong(3, row.getId());
                        statement.setLong(4, row.getUserId());
                        statement.setInt(5, row.getVersion());

                        if (statement.executeUpdate() != 1) {
                            throw new IllegalStateException(
                                    "回填失败，id=" + row.getId());
                        }

                        changed++;
                    }
                }

                connection.commit();

                System.out.println("回填完成，更新行数：" + changed);
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        }
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "请设置环境变量：" + name);
        }

        return value;
    }
}