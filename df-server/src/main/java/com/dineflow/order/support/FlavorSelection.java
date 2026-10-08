package com.dineflow.order.support;

import static com.dineflow.constant.BusinessErrorCode.ITEM_NOT_AVAILABLE;
import static com.dineflow.order.support.SettlementErrors.error;

import com.dineflow.entity.DishFlavor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** 兼容现有逗号分隔协议。规范化键不依赖客户端的选择顺序，不允许猜测有歧义的口味组。 */
@Component
@RequiredArgsConstructor
public class FlavorSelection {
    private final ObjectMapper json;

    public String normalize(String input) {
        if (input == null || input.trim().isEmpty()) return "";
        if (input.length() > 50 || input.chars().anyMatch(Character::isISOControl))
            throw error(ITEM_NOT_AVAILABLE);
        String[] values = input.split(",", -1);
        if (values.length > 10) throw error(ITEM_NOT_AVAILABLE);
        for (int i = 0; i < values.length; i++) {
            values[i] = values[i].trim();
            if (values[i].isEmpty()) throw error(ITEM_NOT_AVAILABLE);
        }
        // 与 SQL 的 utf8mb4_bin 排序对齐。
        Arrays.sort(
                values,
                (a, b) ->
                        Arrays.compareUnsigned(
                                a.getBytes(StandardCharsets.UTF_8),
                                b.getBytes(StandardCharsets.UTF_8)));
        return String.join(",", values);
    }

    public String validate(String value, List<DishFlavor> groups) {
        String key = normalize(value);
        List<String> values = key.isEmpty() ? List.of() : Arrays.asList(key.split(",", -1));
        if (groups.size() != values.size() || groups.size() > 10) throw error(ITEM_NOT_AVAILABLE);
        List<Set<String>> options = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (DishFlavor group : groups) {
            if (group.getName() == null || !names.add(group.getName()))
                throw error(ITEM_NOT_AVAILABLE);
            try {
                JsonNode array = json.readTree(group.getValue());
                if (array == null || !array.isArray() || array.isEmpty())
                    throw error(ITEM_NOT_AVAILABLE);
                Set<String> set = new HashSet<>();
                for (JsonNode option : array) {
                    if (!option.isTextual()
                            || option.asText().trim().isEmpty()
                            || option.asText().contains(",")) throw error(ITEM_NOT_AVAILABLE);
                    set.add(option.asText().trim());
                }
                options.add(set);
            } catch (java.io.IOException | IllegalArgumentException e) {
                throw error(ITEM_NOT_AVAILABLE);
            }
        }
        // 旧接口只有“微辣,热”这样的值，没有每组规格 ID。
        // 因此需要逐组尝试匹配：必须每组恰好选一个，而且只能得出一种解释。
        // assign 是局部回溯：尝试一个值，进入下一组，再撤销尝试。最多找到两种就停止。
        Set<List<String>> matches = new HashSet<>();
        assign(
                0,
                values,
                options,
                new boolean[values.size()],
                new ArrayList<>(),
                matches,
                new int[1]);
        if (matches.size() != 1) throw error(ITEM_NOT_AVAILABLE);
        return key;
    }

    private void assign(
            int index,
            List<String> values,
            List<Set<String>> options,
            boolean[] used,
            List<String> chosen,
            Set<List<String>> matches,
            int[] steps) {
        // 防止畸形配置导致组合搜索无界增长。
        if (++steps[0] > 10000) throw error(ITEM_NOT_AVAILABLE);
        if (matches.size() > 1) return;
        if (index == options.size()) {
            matches.add(List.copyOf(chosen));
            return;
        }
        Set<String> tried = new HashSet<>();
        for (int i = 0; i < values.size(); i++)
            if (!used[i]
                    && tried.add(values.get(i))
                    && options.get(index).contains(values.get(i))) {
                used[i] = true;
                chosen.add(values.get(i));
                assign(index + 1, values, options, used, chosen, matches, steps);
                chosen.remove(chosen.size() - 1);
                used[i] = false;
            }
    }

    /** 摘要中的口味定义排序稳定，选项展示顺序变化不会产生假冲突。 */
    public String definition(List<DishFlavor> groups) {
        try {
            List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
            for (DishFlavor group :
                    groups.stream().sorted(Comparator.comparing(DishFlavor::getName)).toList()) {
                Set<String> values = new TreeSet<>();
                json.readTree(group.getValue()).forEach(v -> values.add(v.asText().trim()));
                Map<String, Object> row = new TreeMap<>();
                row.put("name", group.getName());
                row.put("values", values);
                rows.add(row);
            }
            return json.writeValueAsString(rows);
        } catch (java.io.IOException e) {
            throw error(ITEM_NOT_AVAILABLE);
        }
    }
}
