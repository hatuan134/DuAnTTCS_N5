package com.duanttcsn5.library.ai;

import com.duanttcsn5.library.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.*;
import static com.duanttcsn5.library.ai.AiTypes.*;

/** Account-scoped, database-backed conversation history. Never exposes one account's threads to another. */
@Component
public class AiHistoryStore {
    public record ThreadSummary(String id, String title, OffsetDateTime updatedAt) {}
    public record Entry(String role, String text, List<Block> blocks) {}
    public record ThreadDetail(String id, String title, List<Entry> messages, int turnsRemaining) {}
    public record Resume(List<Map<String,Object>> context, int turns, List<Map<String,Object>> books,
                         String domain, String cardStatus) {}

    private final JdbcTemplate db;
    private final AiAccess access;
    private final ObjectMapper json;
    @Autowired(required=false) private AiSettings settings;
    private int configuredTurns() { return settings==null ? AiChatService.MAX_TURNS : settings.turnLimit(); }
    private int storedTurns(String id) {
        Long count=db.queryForObject("SELECT count(*) FROM ai_chat_entries WHERE thread_id=? AND role='user'",Long.class,id);
        return count==null?0:(int)Math.min(count,Integer.MAX_VALUE);
    }
    public AiHistoryStore(JdbcTemplate db, AiAccess access, ObjectMapper json) {
        this.db=db;this.access=access;this.json=json;
    }
    private long user() {
        var p=access.principal();
        if(p==null) throw new ApiException(HttpStatus.UNAUTHORIZED,"AI_LOGIN_REQUIRED","Hãy đăng nhập để xem lịch sử trò chuyện.");
        return p.id();
    }
    private String role() { return access.role(); }
    private void check(String id) {
        if(id==null || !id.matches("[0-9a-fA-F-]{36}")) throw missing();
        Integer exists=db.query("SELECT 1 FROM ai_chat_threads WHERE id=? AND user_id=? AND role=? AND updated_at >= now() - interval '30 days'",
            rs -> rs.next()?1:null,id,user(),role());
        if(exists==null) throw missing();
    }
    private static ApiException missing() {
        return new ApiException(HttpStatus.NOT_FOUND,"AI_HISTORY_NOT_FOUND","Không tìm thấy cuộc trò chuyện hoặc bạn không có quyền truy cập.");
    }
    private void prune() {
        db.update("DELETE FROM ai_chat_threads WHERE updated_at < now() - interval '30 days'");
    }
    public List<ThreadSummary> list() {
        long actor=user(); String actorRole=role();
        prune();
        return db.query("SELECT id,title,updated_at FROM ai_chat_threads WHERE user_id=? AND role=? ORDER BY updated_at DESC LIMIT 30",
            (rs,n)->new ThreadSummary(rs.getString(1),rs.getString(2),rs.getObject(3,OffsetDateTime.class)),actor,actorRole);
    }
    public ThreadDetail detail(String id) {
        check(id);
        var title=db.queryForObject("SELECT title FROM ai_chat_threads WHERE id=?",String.class,id);
        // Latest 40 messages, not the FIRST 40: older conversations must still
        // resolve references correctly when an unlimited-turn session is resumed.
        var messages=db.query("SELECT role,content,blocks_json FROM "
                + "(SELECT id,role,content,blocks_json FROM ai_chat_entries "
                + "WHERE thread_id=? ORDER BY id DESC LIMIT 40) recent ORDER BY id ASC",
            (rs,n)->new Entry(rs.getString(1),rs.getString(2),parseBlocks(rs.getString(3))),id);
        int limit=configuredTurns();
        int turns=storedTurns(id);
        return new ThreadDetail(id,title,messages,limit==0?Integer.MAX_VALUE:Math.max(0,limit-turns));
    }
    private List<Block> parseBlocks(String value) {
        try { return Arrays.asList(json.readValue(value,Block[].class)); }
        catch(RuntimeException e) { return List.of(); }
    }
    public Resume resume(String id) {
        var detail=detail(id);
        // Reconstructed context contains prose only. Live tools must be called again for current data.
        var list=new ArrayList<Map<String,Object>>();
        var messages=detail.messages();
        for(var item:messages.subList(Math.max(0,messages.size()-12), messages.size())) {
            boolean privateTool=item.blocks().stream().anyMatch(b->Set.of("loans","reservations","libraryCard","copies","statistics","audit","overview","cardStatus","cardRecords").contains(b.type()));
            String body=privateTool && "assistant".equals(item.role())
                ? "Đã kiểm tra dữ liệu theo quyền ở lượt trước. Muốn biết tình trạng hiện tại cần gọi lại công cụ."
                : item.text();
            list.add(Map.of("role",item.role(),"content",body.substring(0,Math.min(body.length(),1500))));
        }
        List<Map<String,Object>> books=List.of();
        // Only the latest verified book result is used for phrases like "cuốn thứ hai".
        // Other personal blocks are deliberately excluded from long-term model context.
        for(int i=messages.size()-1;i>=0;i--) {
            var current=AiConversationContext.rememberedBooks(messages.get(i).blocks());
            if(!current.isEmpty()) {books=current;break;}
        }
        String domain="",cardStatus="";
        // Recover only verified topic/status from saved blocks. No private names enter LLM context.
        for(int i=messages.size()-1;i>=0;i--) {
            var entry=messages.get(i);
            if(!"assistant".equals(entry.role())) continue;
            domain=AiConversationContext.lastDomain(entry.blocks());
            if(!domain.isBlank()) {
                if("cards".equals(domain)) {
                    for(var b:entry.blocks()) if("cardRecords".equals(b.type()))
                        cardStatus=Objects.toString(b.meta().get("status"),"");
                    // Walk backward to the last verified card status, not just the last utterance.
                    if(cardStatus.isBlank()) for(int k=i-1;k>=0 && cardStatus.isBlank();k--) {
                        var previous=messages.get(k);
                        if("assistant".equals(previous.role()) && !AiConversationContext.lastDomain(previous.blocks()).isBlank()
                            && !"cards".equals(AiConversationContext.lastDomain(previous.blocks()))) break;
                        if("assistant".equals(previous.role())) for(var b:previous.blocks())
                            if("cardRecords".equals(b.type()) && b.meta().get("status") instanceof String v && !v.isBlank()) cardStatus=v;
                        if("user".equals(previous.role()))cardStatus=AiConversationContext.cardStatus(previous.text(),cardStatus);
                    }
                }
                break;
            }
        }
        return new Resume(list,storedTurns(id),books,domain,cardStatus);
    }
    @Transactional
    public void save(String id,String prompt,ChatResponse response) {
        long actor=user(); String actorRole=role();
        prune();
        String title=prompt.replaceAll("\\s+"," ").trim();
        title=title.substring(0,Math.min(title.length(),100));
        db.update("INSERT INTO ai_chat_threads(id,user_id,role,title) VALUES(?,?,?,?) ON CONFLICT(id) DO NOTHING",id,actor,actorRole,title);
        check(id); // Prevent forged conversation IDs from writing into somebody else's thread.
        db.update("INSERT INTO ai_chat_entries(thread_id,role,content,blocks_json) VALUES(?,'user',?,'[]')",id,prompt);
        db.update("INSERT INTO ai_chat_entries(thread_id,role,content,blocks_json) VALUES(?,'assistant',?,?)",id,response.message(),json.writeValueAsString(response.blocks()));
        db.update("UPDATE ai_chat_threads SET updated_at=now() WHERE id=?",id);
        // Bound storage even if a user opens unlimited conversations.
        db.update("DELETE FROM ai_chat_threads WHERE user_id=? AND role=? AND id NOT IN (SELECT id FROM ai_chat_threads WHERE user_id=? AND role=? ORDER BY updated_at DESC LIMIT 30)",actor,actorRole,actor,actorRole);
    }
    @Transactional
    public void delete(String id) { check(id);db.update("DELETE FROM ai_chat_threads WHERE id=?",id); }
}
