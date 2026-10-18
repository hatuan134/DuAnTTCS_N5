package com.duanttcsn5.library.ai;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.*;
import java.nio.charset.StandardCharsets;

@Component
public class AiRequestSizeFilter extends OncePerRequestFilter {
    @Override protected boolean shouldNotFilter(HttpServletRequest r) { return !r.getRequestURI().equals(r.getContextPath()+"/api/v1/ai/chat") || !"POST".equals(r.getMethod()); }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws IOException,ServletException {
        response.setHeader("Cache-Control","no-store");
        byte[] bytes=request.getInputStream().readNBytes(8193);
        if(bytes.length>8192) {
            response.setStatus(413);response.setContentType("application/json;charset=UTF-8");response.setHeader("Cache-Control","no-store");
            response.getWriter().write("{\"code\":\"AI_REQUEST_TOO_LARGE\",\"message\":\"Nội dung quá dài. Hãy rút gọn câu hỏi.\"}");return;
        }
        chain.doFilter(new HttpServletRequestWrapper(request) {
            @Override public ServletInputStream getInputStream() {
                var input=new ByteArrayInputStream(bytes);
                return new ServletInputStream() {
                    public int read() { return input.read(); }
                    public boolean isFinished() { return input.available()==0; }
                    public boolean isReady() { return true; }
                    public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException("Synchronous request only"); }
                };
            }
            @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(),StandardCharsets.UTF_8)); }
        },response);
    }
}
