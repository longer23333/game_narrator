package cn.longer233.gamenarrator.common;

import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Keeps existing H2 MERGE semantics while translating the same statement to PostgreSQL ON CONFLICT. */
public final class PortableUpsert {
    private static final Pattern MERGE = Pattern.compile(
            "(?is)\\s*MERGE\\s+INTO\\s+(\\w+)\\s*\\((.*?)\\)\\s*KEY\\s*\\((.*?)\\)\\s*VALUES\\s*\\((.*)\\)\\s*");
    private PortableUpsert() {}

    public static int update(JdbcTemplate jdbc, String h2MergeSql, String conflictColumns, Object... arguments) {
        if (!isPostgreSql(jdbc)) return jdbc.update(h2MergeSql, arguments);
        return jdbc.update(toPostgreSql(h2MergeSql, conflictColumns), arguments);
    }

    static String toPostgreSql(String h2MergeSql, String conflictColumns) {
        var match = MERGE.matcher(h2MergeSql);
        if (!match.matches()) throw new IllegalArgumentException("Unsupported MERGE statement");
        String table = match.group(1);
        String columns = match.group(2);
        String keys = match.group(3);
        String values = match.group(4);
        Set<String> keySet = Arrays.stream(conflictColumns.split(","))
                .map(value -> value.trim().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        String updates = Arrays.stream(columns.split(","))
                .map(String::trim).filter(column -> !keySet.contains(column.toLowerCase(Locale.ROOT)))
                .map(column -> column + "=EXCLUDED." + column).collect(Collectors.joining(","));
        String action = updates.isBlank() ? "DO NOTHING" : "DO UPDATE SET " + updates;
        return "INSERT INTO " + table + "(" + columns + ") VALUES(" + values + ") ON CONFLICT ("
                + keys + ") " + action;
    }

    private static boolean isPostgreSql(JdbcTemplate jdbc) {
        if (jdbc.getDataSource() == null) return false;
        try (Connection connection = jdbc.getDataSource().getConnection()) {
            return connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("postgresql");
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to identify database dialect", exception);
        }
    }
}
