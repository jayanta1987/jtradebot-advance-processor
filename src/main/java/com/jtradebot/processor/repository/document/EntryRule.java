package com.jtradebot.processor.repository.document;

import com.jtradebot.processor.model.enums.EntryReason;
import com.jtradebot.processor.model.enums.TradeMode;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
@Data
@Document(collection = "entry_rules")
public class EntryRule {
    @Id
    private String id;

    @Indexed(unique = true)
    private EntryReason entryReason;

    @Indexed
    private TradeMode tradeMode;

    @Indexed
    private boolean active;

    private String exitNtp;
    private Integer maxQty;
    private List<Condition> conditions;
    private String combineWith; // AND or OR

    @Data
    public static class Condition {
        private String field;
        private String operator; // >, <, ==, !=
        private Object value;
    }
}


