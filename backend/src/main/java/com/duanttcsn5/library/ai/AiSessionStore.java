package com.duanttcsn5.library.ai;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import com.duanttcsn5.library.exception.ApiException;
import org.springframework.http.HttpStatus;

@Component
public class AiSessionStore {
    static final long TTL=30*60*1000L;
    static final int CAPACITY=256;
    public static final class Session {
        final String id;
        final String owner;
        final ReentrantLock lock=new ReentrantLock();
        final List<Map<String,Object>> history=new ArrayList<>();
        volatile long touched=System.currentTimeMillis();
        int turns=0;
        List<Map<String,Object>> lastBooks=List.of();
        String lastDomain="";
        String lastCardStatus="";
        Session(String owner) { this(UUID.randomUUID().toString(),owner); }
        Session(String id, String owner) { this.id=id; this.owner=owner; }
    }
    private final Map<String,Session> sessions=new LinkedHashMap<>();
    private final AiHistoryStore history;
    public AiSessionStore() { this.history=null; } // Unit tests run without PostgreSQL.
    @Autowired public AiSessionStore(AiHistoryStore history) { this.history=history; }
    public synchronized Session acquire(String id,String owner) {
        long now=System.currentTimeMillis();
        sessions.values().removeIf(s->now-s.touched>TTL && !s.lock.isLocked());
        Session s;
        if(id==null || id.isBlank()) {
            if(sessions.size()>=CAPACITY) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"AI_BUSY","Trợ lý đang bận. Vui lòng thử lại sau.");
            s=new Session(owner); sessions.put(s.id,s);
        } else {
            s=sessions.get(id);
            if(s==null && history!=null && owner.startsWith("user:")) {
                if(sessions.size()>=CAPACITY) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"AI_BUSY","Trợ lý đang bận. Vui lòng thử lại sau.");
                var saved=history.resume(id); // Checks authenticated user and role.
                s=new Session(id,owner);
                s.history.addAll(saved.context());
                s.turns=saved.turns();
                s.lastBooks=saved.books();
                s.lastDomain=saved.domain();
                s.lastCardStatus=saved.cardStatus();
                sessions.put(id,s);
            }
            if(s==null || !s.owner.equals(owner) || now-s.touched>TTL)
                throw new ApiException(HttpStatus.GONE,"AI_SESSION_EXPIRED","Cuộc trò chuyện không còn khả dụng. Hãy tạo cuộc trò chuyện mới.");
        }
        if(!s.lock.tryLock()) throw new ApiException(HttpStatus.CONFLICT,"AI_SESSION_BUSY","Vui lòng đợi câu trả lời hiện tại.");
        s.touched=now; return s;
    }
    public synchronized void delete(String id,String owner) {
        var s=sessions.get(id);
        if(s!=null && s.owner.equals(owner)) {
            if(!s.lock.tryLock()) throw new ApiException(HttpStatus.CONFLICT,"AI_SESSION_BUSY","Cuộc trò chuyện đang xử lý. Vui lòng thử lại.");
            try { s.history.clear(); sessions.remove(id); } finally { s.lock.unlock(); }
        }
    }
}
