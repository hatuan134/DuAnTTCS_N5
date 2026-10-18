package com.duanttcsn5.library.ai;

import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.Semaphore;
import com.duanttcsn5.library.exception.ApiException;
import org.springframework.http.HttpStatus;

@Component
public class AiRateLimiter {
    private record Bucket(long minute,int count) {}
    private final Map<String,Bucket> buckets=new HashMap<>();
    private final Semaphore active;
    private final AiSettings settings;
    public AiRateLimiter(AiSettings settings) { this.settings=settings;this.active=new Semaphore(settings.concurrency()); }
    public synchronized void check(String owner,boolean guest) {
        long minute=System.currentTimeMillis()/60000;
        buckets.values().removeIf(b->b.minute()!=minute);
        var b=buckets.getOrDefault(owner,new Bucket(minute,0));
        var global=buckets.getOrDefault("GLOBAL",new Bucket(minute,0));
        int perUser=guest?settings.guestLimit():settings.userLimit();
        int globalMax=settings.globalLimit();
        if(buckets.size()>=4096 || (perUser>0 && b.count()>=perUser)
            || (globalMax>0 && global.count()>=globalMax))
            throw limited();
        buckets.put(owner,new Bucket(minute,b.count()+1)); buckets.put("GLOBAL",new Bucket(minute,global.count()+1));
    }
    public void enter() { if(!active.tryAcquire())throw limited(); }
    public void leave() { active.release(); }
    private ApiException limited() { return new ApiException(HttpStatus.TOO_MANY_REQUESTS,"AI_RATE_LIMIT","Trợ lý đang nhận nhiều yêu cầu. Vui lòng đợi một phút rồi thử lại."); }
}
