package com.duanttcsn5.library.ai;

import org.junit.jupiter.api.Test;
import java.util.*;
import static com.duanttcsn5.library.ai.AiTypes.*;
import static org.assertj.core.api.Assertions.*;

class AiConversationUpgradeTest {
    private final Block books=new Block("books","Kết quả tra cứu",List.of(
        Map.of("id",10L,"title","Java cơ bản","availableCount",2L),
        Map.of("id",20L,"title","Java nâng cao","availableCount",0L),
        Map.of("id",30L,"title","Spring Boot","availableCount",1L)),
        Map.of("total",3L),List.of());

    @Test void ordinalReferenceUsesVerifiedBookIdentifiersOnly() {
        var context=AiConversationContext.rememberedBooks(List.of(books));
        assertThat(AiConversationContext.referencedBook("Cuốn thứ hai còn không?",context)).contains(20L);
        assertThat(AiConversationContext.referencedBook("Quyển số 3 là gì?",context)).contains(30L);
        assertThat(AiConversationContext.referencedBook("Cuốn thứ 8 còn không?",context)).isEmpty();
        assertThat(AiConversationContext.referencedBook("Cuốn thứ hai",List.of())).isEmpty();
    }
    @Test void pluralFollowupsStayWithinVerifiedPreviousResults() {
        assertThat(AiConversationContext.isPluralBookReference("Những cuốn đó có còn không?")).isTrue();
        assertThat(AiConversationContext.isPluralBookReference("Cuốn thứ hai còn không?")).isFalse();
        assertThat(AiConversationContext.rememberedBooks(List.of(books))).hasSize(3);
    }
    @Test void bookAnswerUsesOnlyVerifiedCountsAndHelpsContinue() {
        var result=AiNaturalAnswer.compose("Có sách nào học Java không?",List.of(books));
        assertThat(result).contains("Java cơ bản","Java nâng cao","sẵn sàng: 0 bản","cuốn thứ hai")
            .doesNotContain("Có 999");
    }
    @Test void zeroResultsCannotBePresentedAsGuessedLoans() {
        var result=AiNaturalAnswer.compose("Đơn của tôi?",List.of(
            new Block("reservations","Đơn đặt giữ của bạn",List.of(),Map.of("total",0),List.of())));
        assertThat(result).contains("0 kết quả").doesNotContain("9 đơn");
    }
    @Test void baselineInventoryAndPickupRoutingOnlyRecognizesSpecificQuestions() {
        assertThat(AiChatService.isInventoryRequest("Hiện tại có bao nhiêu sách trên hệ thống?")).isTrue();
        assertThat(AiChatService.isInventoryRequest("Có bao nhiêu phiếu mượn?")).isFalse();
        assertThat(AiChatService.isPickupRequest("Hiện có đơn sách nào đang chờ nhận không?")).isTrue();
        assertThat(AiChatService.isPickupRequest("Đơn đặt giữ của tôi đang chờ nhận?")).isFalse();
    }
}
