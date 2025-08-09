
package com.jtradebot.processor.repository;

import com.jtradebot.processor.repository.document.Instrument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface InstrumentRepository extends MongoRepository<Instrument, String> {
    List<Instrument> findByStrikeAndNameAndInstrumentTypeOrderByExpiryAsc(String strikePrice, String name, String instrumentType);

    Optional<Instrument> findByTradingSymbol(String tradingSymbol);

    Optional<Instrument> findByExpiryAndNameAndInstrumentTypeAndStrike(Date expiry, String name, String instrumentType, String strike);
    
    /**
     * Find all Nifty future instruments
     * @param name Instrument name (e.g., "NIFTY")
     * @param instrumentType Instrument type (e.g., "FUT")
     * @param segment Segment (e.g., "NFO-FUT")
     * @return List of matching instruments
     */
    List<Instrument> findByNameAndInstrumentTypeAndSegmentOrderByExpiryAsc(String name, String instrumentType, String segment);
}
