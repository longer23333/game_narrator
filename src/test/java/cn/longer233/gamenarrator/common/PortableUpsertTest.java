package cn.longer233.gamenarrator.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PortableUpsertTest {
    @Test void translatesH2MergeToPostgresqlConflictUpdate() {
        String sql = PortableUpsert.toPostgreSql("""
                MERGE INTO sample(id,owner_id,value,updated_at) KEY(owner_id,value)
                VALUES(?,?,COALESCE((SELECT value FROM sample WHERE owner_id=?),?),?)
                """, "owner_id,value");

        assertThat(sql).contains("INSERT INTO sample(id,owner_id,value,updated_at)")
                .contains("ON CONFLICT (owner_id,value) DO UPDATE SET")
                .contains("id=EXCLUDED.id")
                .contains("updated_at=EXCLUDED.updated_at");
    }
}
