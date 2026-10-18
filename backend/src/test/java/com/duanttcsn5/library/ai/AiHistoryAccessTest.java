package com.duanttcsn5.library.ai;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiHistoryAccessTest {
    @Test void guestsCannotListOrReadOrWriteStoredHistory() {
        SecurityContextHolder.clearContext();
        var db=mock(JdbcTemplate.class);
        var history=new AiHistoryStore(db,new AiAccess(),JsonMapper.builder().build());
        assertThatThrownBy(history::list).hasMessageContaining("đăng nhập");
        assertThatThrownBy(()->history.detail("00000000-0000-0000-0000-000000000001")).hasMessageContaining("đăng nhập");
        verifyNoInteractions(db); // The database must never be queried for anonymous history.
    }
}
