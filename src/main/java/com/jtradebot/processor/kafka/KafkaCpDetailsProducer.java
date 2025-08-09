package com.jtradebot.processor.kafka;

import com.jtradebot.processor.model.event.ProcessedScoreEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaCpDetailsProducer {

    private final KafkaTemplate<String, ProcessedScoreEvent> kafkaTemplate;

    @Async
    public void sendCpDetails(ProcessedScoreEvent processedScoreEvent) {
        // The tradingSymbol will be used as the partition key
        String partitionKey = String.valueOf(processedScoreEvent.getInstrumentToken());
        String TOPIC = "jtradebot-processor-cpdetails";
        kafkaTemplate.send(TOPIC, partitionKey, processedScoreEvent);
    }

}
