package cn.longer233.gamenarrator.observability;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:prometheus-test",
        "game-narrator.storage-root=./target/prometheus-test-storage",
        "game-narrator.capacity.minimum-free-bytes=0",
        "game-narrator.capacity.minimum-free-percent=0"
})
@AutoConfigureMockMvc
class PrometheusEndpointTest {
    @Autowired MockMvc mvc;

    @Test
    void exposesApplicationAndJvmMetrics() throws Exception {
        mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("jvm_memory_used_bytes")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("game_narrator_task_queue_size")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("game_narrator_storage_usable_bytes")));
    }
}
