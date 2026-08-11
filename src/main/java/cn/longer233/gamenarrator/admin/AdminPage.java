package cn.longer233.gamenarrator.admin;

import java.util.List;
import java.util.Map;

public record AdminPage(List<Map<String,Object>> items, long total, int page, int size, int totalPages) {
    static AdminPage of(List<Map<String,Object>> items, long total, int page, int size) {
        return new AdminPage(items, total, page, size, Math.max(1, (int)Math.ceil(total / (double)size)));
    }
}
