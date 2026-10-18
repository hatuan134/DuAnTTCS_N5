package com.duanttcsn5.library.ai;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;

/** Fixed, parameterized query. No SQL text or reader ID is accepted from an AI tool. */
@Repository
public class AiReadRepository {
    private final JdbcTemplate jdbc;
    public AiReadRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Map<Long,Long> ownedLoanIds(Long readerId, List<Long> itemIds) {
        Map<Long,Long> result=new HashMap<>();
        if (itemIds.isEmpty()) return result;
        String placeholders=String.join(",",Collections.nCopies(itemIds.size(),"?"));
        List<Object> args=new ArrayList<>(); args.add(readerId); args.addAll(itemIds);
        jdbc.query("SELECT li.id, li.loan_id FROM loan_items li JOIN loans l ON l.id=li.loan_id WHERE l.borrower_user_id=? AND li.id IN ("+placeholders+")",
            rs->{ result.put(rs.getLong(1),rs.getLong(2)); },args.toArray());
        return result;
    }
}
