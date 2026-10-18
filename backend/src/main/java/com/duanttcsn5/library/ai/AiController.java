package com.duanttcsn5.library.ai;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import com.duanttcsn5.library.exception.ApiException;
import static com.duanttcsn5.library.ai.AiTypes.*;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {
    private static final Logger LOG=LoggerFactory.getLogger(AiController.class);
    private final AiChatService chat;
    private final AiAccess access;
    private final ObjectMapper json;
    public AiController(AiChatService chat,AiAccess access,ObjectMapper json) { this.chat=chat;this.access=access;this.json=json; }
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest body,HttpServletRequest request) {
        checkToken(request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(chat.chat(body,request.getRemoteAddr()));
    }
    /** POST + SSE allows normal JWT headers (native EventSource cannot send bearer JWT). */
    @PostMapping(value="/chat/stream",produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> stream(@Valid @RequestBody ChatRequest body,HttpServletRequest request) {
        checkToken(request);
        String remote=request.getRemoteAddr();
        SecurityContext security=SecurityContextHolder.getContext();
        StreamingResponseBody response=output->{
            SecurityContextHolder.setContext(security);
            try {
                event(output,"status",Map.of("message","Đang kiểm tra câu hỏi…"));
                var result=chat.chat(body,remote,part->{
                    try {event(output,"delta",Map.of("text",part));}
                    catch(IOException e) {throw new StreamClosed(e);}
                });
                event(output,"done",result);
            } catch(StreamClosed ignored) {
                // Client went away; session/rate-limiter cleanup is in AiChatService.
            } catch(ApiException ex) {
                // After SSE headers are committed HTTP remains 200. Report failures as
                // an explicit event:error with a safe error code instead of silent success.
                String code=ex.getCode();
                LOG.warn("LIBRA_AI SSE failed: code={}",code);
                String message=code.startsWith("AI_") ? ex.getMessage() : "LIBRA AI chưa hoàn tất yêu cầu. Vui lòng thử lại.";
                try {event(output,"error",Map.of("code",code.startsWith("AI_")?code:"AI_REQUEST_FAILED","message",message));}
                catch(IOException ignored) {}
            } catch(Exception ex) {
                // Never log user input, tokens, provider body or SQL details.
                LOG.warn("LIBRA_AI SSE unexpected failure: type={}",ex.getClass().getSimpleName());
                try {event(output,"error",Map.of("code","AI_STREAM_ERROR","message","Không thể hoàn tất trả lời. Kiểm tra kết nối hoặc hạn mức AI và thử lại."));}
                catch(IOException ignored) {}
            } finally {SecurityContextHolder.clearContext();}
        };
        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL,"no-store, no-transform")
            .header("X-Accel-Buffering","no")
            .contentType(MediaType.TEXT_EVENT_STREAM).body(response);
    }
    private static class StreamClosed extends RuntimeException {StreamClosed(IOException e){super(e);}}
    private void event(OutputStream output,String type,Object data) throws IOException {
        String value="event: "+type+"\ndata: "+json.writeValueAsString(data)+"\n\n";
        output.write(value.getBytes(StandardCharsets.UTF_8));
        output.flush();
    }
    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id,HttpServletRequest request) {
        checkToken(request); chat.delete(id,request.getRemoteAddr());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    @GetMapping("/history")
    public ResponseEntity<List<AiHistoryStore.ThreadSummary>> history(HttpServletRequest request) {
        checkToken(request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(chat.listHistory());
    }
    @GetMapping("/history/{id}")
    public ResponseEntity<AiHistoryStore.ThreadDetail> historyDetail(@PathVariable String id,HttpServletRequest request) {
        checkToken(request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(chat.getHistory(id));
    }
    @DeleteMapping("/history/{id}")
    public ResponseEntity<Void> deleteHistory(@PathVariable String id,HttpServletRequest request) {
        checkToken(request); chat.deleteHistory(id,request.getRemoteAddr());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    private void checkToken(HttpServletRequest request) {
        // Public endpoint must not silently downgrade an expired/invalid bearer token to guest.
        if(request.getHeader("Authorization")!=null && access.principal()==null)
            throw new ApiException(HttpStatus.UNAUTHORIZED,"AI_SESSION_INVALID","Phiên đăng nhập hết hạn. Vui lòng đăng nhập lại.");
    }
}
