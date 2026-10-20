package com.duanttcsn5.library.ai;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.*;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;

class AiRequestSizeFilterTest {
    @ParameterizedTest @ValueSource(strings={"/api/v1/ai/chat","/api/v1/ai/chat/stream"})
    void bothEndpointsRejectOversizeAndReplayAcceptedUtf8Body(String endpoint) throws Exception {
        for(int size:new int[]{8192,8193}) {
            var request=new MockHttpServletRequest("POST","/libra"+endpoint);
            request.setContextPath("/libra");
            byte[] body=("ấ".repeat(size/3)+"x".repeat(size%3)).getBytes(StandardCharsets.UTF_8);
            request.setContent(body);
            var response=new MockHttpServletResponse();
            boolean[] reached={false};
            new AiRequestSizeFilter().doFilter(request,response,(req,res)->{
                reached[0]=true;
                assertThat(req.getInputStream().readAllBytes()).isEqualTo(body);
            });
            assertThat(reached[0]).isEqualTo(size==8192);
            assertThat(response.getStatus()).isEqualTo(size==8192?200:413);
            assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        }
    }
}
