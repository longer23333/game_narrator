package cn.longer233.gamenarrator.common;

import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.connector.ClientAbortException;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiExceptionHandlerTest {

    @Test
    void doesNotTryToSerializeJsonAfterMediaClientDisconnects() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new DisconnectingMediaController())
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        mockMvc.perform(get("/test/disconnected-media"))
                .andExpect(status().isOk())
                .andExpect(content().bytes(new byte[0]));
    }

    @RestController
    private static class DisconnectingMediaController {
        @GetMapping(value = "/test/disconnected-media", produces = "video/mp4")
        void stream(HttpServletResponse response) throws ClientAbortException {
            response.setContentType("video/mp4");
            throw new ClientAbortException("Connection reset by peer");
        }
    }
}
