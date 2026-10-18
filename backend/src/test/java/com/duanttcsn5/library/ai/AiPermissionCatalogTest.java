package com.duanttcsn5.library.ai;

import com.duanttcsn5.library.service.PermissionReviewCatalog;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** No database or external model calls. Documents the actual six AI routes. */
class AiPermissionCatalogTest {
    @Test void aiRoutesAreExplicitlyRecordedInThePermissionMatrix() throws Exception {
        var catalog = new PermissionReviewCatalog(JsonMapper.builder().build());
        var handlers = List.of("chat", "stream", "delete", "history", "historyDetail", "deleteHistory");
        for (String handler : handlers) {
            var entry = catalog.find("AiController." + handler).orElseThrow();
            assertThat(entry.controller()).isEqualTo("AiController");
            assertThat(entry.module()).isEqualTo("AI");
            assertThat(entry.httpMethods()).hasSize(1);
            assertThat(entry.rules().keySet()).containsExactlyInAnyOrder(
                    "ADMIN", "LIBRARY_MANAGER", "LIBRARIAN", "READER");
        }
    }

    @Test void guestHistoryIsNotDocumentedAsPublicAndChatIsPublic() throws Exception {
        var catalog = new PermissionReviewCatalog(JsonMapper.builder().build());
        for (String handler : List.of("chat", "stream", "delete")) {
            var entry = catalog.find("AiController." + handler).orElseThrow();
            assertThat(Set.copyOf(entry.rules().values())).containsExactly("PUBLIC");
        }
        for (String handler : List.of("history", "historyDetail", "deleteHistory")) {
            var entry = catalog.find("AiController." + handler).orElseThrow();
            assertThat(entry.rules().values()).doesNotContain("PUBLIC");
        }
    }
}
