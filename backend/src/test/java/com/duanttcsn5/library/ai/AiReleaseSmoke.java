package com.duanttcsn5.library.ai;

import tools.jackson.databind.ObjectMapper;
import java.util.*;
import static com.duanttcsn5.library.ai.AiTypes.*;

/** Offline verification: java -cp '<main classes>:<dependencies>:<test classes>' com.duanttcsn5.library.ai.AiReleaseSmoke */
public final class AiReleaseSmoke {
    private AiReleaseSmoke() {}
    private static final class RoleAccess extends AiAccess {
        String selected;
        RoleAccess(String selected) { this.selected=selected; }
        @Override public String role() { return selected; }
    }
    private static void require(boolean condition, String error) {
        if (!condition) throw new AssertionError(error);
    }
    private static Set<String> names(AiTools tools) {
        Set<String> result=new HashSet<>();
        for(var d:tools.definitions()) result.add((String)((Map<?,?>)d.get("function")).get("name"));
        return result;
    }
    private static AiTools tools(RoleAccess role) {
        return new AiTools(role,new AiNavigation(role),null,null,null,null,null,null,null,null,null,null);
    }
    public static void main(String[] args) {
        var role=new RoleAccess("PUBLIC");
        var tools=tools(role);
        require(names(tools).containsAll(Set.of("searchBooks","getAvailableFeatures")),"Missing public tools");
        require(!names(tools).contains("getMyProfileOverview"),"Public private-profile leak");
        require(!names(tools).contains("getReminderSummary"),"Public reminder leak");
        role.selected="READER";
        require(names(tools).containsAll(Set.of("getMyProfileOverview","getMyLoans","getMyReservations")),"Missing reader tools");
        require(!names(tools).contains("getPendingFeeReductions"),"Reader pending-fee leak");
        role.selected="LIBRARIAN";
        require(names(tools).containsAll(Set.of("getReminderSummary","getCompensationFactors","searchAuthorizedReaders")),"Missing librarian tools");
        require(!names(tools).contains("getOverdueDebtSummary"),"Librarian access to manager report");
        role.selected="LIBRARY_MANAGER";
        require(names(tools).containsAll(Set.of("getOverdueDebtSummary","getPendingFeeReductions")),"Missing manager tools");
        role.selected="ADMIN";
        require(names(tools).contains("getOverdueDebtSummary"),"Admin report missing");
        require(names(tools).contains("getPendingFeeReductions"),"Admin pending fee reductions missing");
        role.selected="PUBLIC";
        var json=new ObjectMapper();
        var empty=json.readTree("{}");
        var current=json.readTree("{\"publicationYear\":2026}");
        require(Objects.equals(AiTools.publicationYear(current,"Java"),2026),"Explicit year parsing");
        require(Objects.equals(AiTools.publicationYear(empty,"Tìm sách xuất bản năm 2026"),2026),"Question year parsing");
        require("Java".equals(AiTools.withoutPublicationYear("Java xuất bản năm 2026",2026)),"Year keyword cleanup");
        var b=new Block("cardStatus","Thống kê thẻ",List.of(
            Map.of("status","ACTIVE","value",36L),Map.of("status","LOCKED","value",1L)),
            Map.of("total",37L),List.of());
        String answer=AiNaturalAnswer.compose("Trong số đó có bao nhiêu thẻ bị khóa?",List.of(b));
        require(answer.contains("37") && answer.contains("1"),"Card answer must use verified facts");
        require(!AiGroundingCheck.supported("Thư viện có 900 thẻ",List.of(b)),"Fabricated count accepted");
        require(new AiSettings(new org.springframework.mock.env.MockEnvironment()).turnLimit()==0,"Chat turn cap must default to disabled");
        System.out.println("PASS: role-scoped tools, year filters, grounded card answer, bounded chat context");
    }
}
