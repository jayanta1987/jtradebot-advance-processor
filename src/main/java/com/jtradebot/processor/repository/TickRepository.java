
package com.jtradebot.processor.repository;

import com.jtradebot.tickstore.repository.CalculatedTick;
import com.jtradebot.tickstore.model.DateResult;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.Date;
import java.util.List;

public interface TickRepository extends MongoRepository<CalculatedTick, String> {
    @Query(value = "{ 'tick.tickTimestamp': { $gte: ?0, $lte: ?1 }, 'tick.instrumentToken': ?2 }")
    List<CalculatedTick> findLastTradedTimesByDateRangeAndInstrumentToken(Date fromDate, Date toDate, Long instrumentToken, Sort sort);

    @Query(value = "{}", fields = "{ 'tick.lastTradedTime' : 1 }")
    List<CalculatedTick> findAllLastTradedTimes();
    

    
    /**
     * Get list of unique dates for verification
     */
    @Aggregation(pipeline = {
        "{ $group: { _id: { $dateToString: { format: '%Y-%m-%d', date: '$tick.tickTimestamp' } } } }",
        "{ $project: { date: '$_id' } }",
        "{ $sort: { date: 1 } }"
    })
    List<DateResult> getUniqueDates();
}

