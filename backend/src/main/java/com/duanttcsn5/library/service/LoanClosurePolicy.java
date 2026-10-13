package com.duanttcsn5.library.service;
import java.util.Collection;
/** Historical item reasons determine loan state, never current copy inventory state. */
public final class LoanClosurePolicy {
    private LoanClosurePolicy() {}
    public static String item(java.time.OffsetDateTime closedAt,String reason) {
        return closedAt==null?"BORROWED":reason==null?"RETURNED":reason;
    }
    public static String loan(Collection<String> states) {
        if(states.isEmpty()) return "EMPTY";
        if(states.contains("BORROWED")) return states.stream().allMatch("BORROWED"::equals)?"BORROWED":"PARTIALLY_RETURNED";
        if(states.contains("LOST") && states.contains("DAMAGED")) return "MIXED_INCIDENT";
        if(states.contains("LOST")) return "LOST";
        if(states.contains("DAMAGED")) return "DAMAGED";
        return "RETURNED";
    }
}
