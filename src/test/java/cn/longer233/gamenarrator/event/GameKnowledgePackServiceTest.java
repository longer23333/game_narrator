package cn.longer233.gamenarrator.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GameKnowledgePackServiceTest {
    @Test
    void importsExportsAndRejectsUnsafePackShape() throws Exception {
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:knowledge-pack;DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(dataSource).load().migrate();
        ObjectMapper mapper = new ObjectMapper();
        GameKnowledgePackService service = new GameKnowledgePackService(new JdbcTemplate(dataSource), mapper);
        service.importPack(mapper.readTree("""
                {"code":"my-game-v1","name":"我的游戏","description":"测试", "eventRules":[
                {"code":"WIN","name":"胜利","keywords":["victory"],"baseConfidence":0.8,"importance":90}]}
                """), false);

        assertThat(service.list()).singleElement().satisfies(pack -> assertThat(pack.code()).isEqualTo("my-game-v1"));
        assertThat(service.exportPack("my-game-v1").path("eventRules").get(0).path("code").asText()).isEqualTo("WIN");
        assertThatThrownBy(() -> service.importPack(mapper.readTree("{\"code\":\"../bad\",\"name\":\"x\",\"eventRules\":[]}"), false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
