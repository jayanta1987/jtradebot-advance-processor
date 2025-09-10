
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
    
    /**
     * Find instruments by creation date
     * @param createdAt Creation date in IST format (e.g., "IST-2025-01-19")
     * @return List of instruments created on the specified date
     */
    List<Instrument> findByCreatedAt(String createdAt);
    
    /**
     * Count instruments by creation date (optimized for performance)
     * @param createdAt Creation date in IST format (e.g., "IST-2025-01-19")
     * @return Count of instruments created on the specified date
     */
    long countByCreatedAt(String createdAt);
    
    /**
     * Check if any instrument exists for the given creation date (most optimized)
     * @param createdAt Creation date in IST format (e.g., "IST-2025-01-19")
     * @return true if at least one instrument exists for the date
     */
    boolean existsByCreatedAt(String createdAt);
}
