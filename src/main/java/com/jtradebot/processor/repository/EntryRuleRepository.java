package com.jtradebot.processor.repository;

import com.jtradebot.processor.model.enums.EntryReason;
import com.jtradebot.processor.model.enums.TradeMode;
import com.jtradebot.processor.repository.document.EntryRule;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EntryRuleRepository extends MongoRepository<EntryRule, String> {

    List<EntryRule> findByTradeModeAndActive(TradeMode tradeMode, boolean isActive);

    Optional<EntryRule> findByEntryReason(EntryReason entryReason);
}
