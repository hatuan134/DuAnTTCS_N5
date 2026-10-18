package com.duanttcsn5.library.ai;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class AiSessionTest {
    @Test void ownershipExpiryAndDeleteAreEnforced() {
        var store=new AiSessionStore();var a=store.acquire(null,"user:1:0:READER");a.lock.unlock();
        assertThatThrownBy(()->store.acquire(a.id,"user:2:0:READER")).hasMessageContaining("không còn khả dụng");
        assertThatThrownBy(()->store.acquire(a.id,"guest:127.0.0.1")).hasMessageContaining("không còn khả dụng");
        assertThatThrownBy(()->store.acquire(a.id,"user:1:1:READER")).hasMessageContaining("không còn khả dụng");
        store.delete(a.id,"user:2:0:READER");var same=store.acquire(a.id,"user:1:0:READER");same.lock.unlock();
        store.delete(a.id,"user:1:0:READER");assertThatThrownBy(()->store.acquire(a.id,"user:1:0:READER")).isInstanceOf(RuntimeException.class);
        var expired=store.acquire(null,"a");expired.touched=System.currentTimeMillis()-AiSessionStore.TTL-1;expired.lock.unlock();
        assertThatThrownBy(()->store.acquire(expired.id,"a")).hasMessageContaining("không còn khả dụng");
    }
    @Test void rateAndConcurrencyAreBounded() {
        var limiter=new AiRateLimiter(new AiSettings(new MockEnvironment().withProperty("AI_GUEST_REQUESTS_PER_MINUTE","2").withProperty("AI_MAX_CONCURRENT","1")));
        limiter.check("guest:1",true);limiter.check("guest:1",true);assertThatThrownBy(()->limiter.check("guest:1",true)).hasMessageContaining("một phút");
        limiter.enter();assertThatThrownBy(limiter::enter).isInstanceOf(RuntimeException.class);limiter.leave();limiter.enter();limiter.leave();
    }
    @Test void knownSecretsAreRedactedBeforeProvider() {
        assertThat(AiChatService.redact("email a@b.invalid password=private Bearer abc.def.ghi gsk_123456789012345678901"))
            .doesNotContain("a@b.invalid","private","abc.def.ghi","gsk_");
    }
}
