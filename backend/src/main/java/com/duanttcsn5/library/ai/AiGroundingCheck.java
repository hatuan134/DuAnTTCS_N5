package com.duanttcsn5.library.ai;

import java.util.*;
import java.util.regex.Pattern;
import static com.duanttcsn5.library.ai.AiTypes.*;

/** Fail closed when free-form LLM prose introduces counts not present in a verified tool result. */
final class AiGroundingCheck {
    private AiGroundingCheck() {}
    private static final Pattern NUMBER=Pattern.compile("(?<![\\p{L}\\p{N}])\\d+(?:[.,]\\d+)*(?![\\p{L}\\p{N}])");
    static boolean supported(String answer,List<Block> blocks) {
        if(answer==null || answer.isBlank())return false;
        Set<String> allowed=new HashSet<>();
        for(var block:blocks) {
            collect(block.items(),allowed);
            collect(block.meta(),allowed);
            allowed.add(Integer.toString(block.items().size()));
        }
        // An ordinal is allowed only when it points into an actual verified result.
        for(var block:blocks) for(int i=1;i<=block.items().size();i++)
            allowed.add(Integer.toString(i));
        var matcher=NUMBER.matcher(answer);
        while(matcher.find()) {
            String v=matcher.group().replace(",","").replace(".","");
            if(!allowed.contains(matcher.group()) && !allowed.contains(v))return false;
        }
        return true;
    }
    private static void collect(Object value,Set<String> allowed) {
        if(value instanceof Number n) {
            allowed.add(n.toString());
        } else if(value instanceof Map<?,?> m) {
            for(var x:m.values())collect(x,allowed);
        } else if(value instanceof Collection<?> c) {
            for(var x:c)collect(x,allowed);
        } else if(value instanceof CharSequence text) {
            var matcher=NUMBER.matcher(text);
            while(matcher.find())allowed.add(matcher.group());
        } else if(value instanceof java.time.temporal.TemporalAccessor date) {
            collect(date.toString(),allowed);
        }
    }
}
