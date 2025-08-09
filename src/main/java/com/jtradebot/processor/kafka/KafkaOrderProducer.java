package com.jtradebot.processor.kafka;

import com.jtradebot.processor.model.event.OrderEntryEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaOrderProducer {

    private final KafkaTemplate<String, OrderEntryEvent> kafkaTemplate;

    @Async
    public void triggerOrder(OrderEntryEvent orderEntryRequest) {
        // The tradingSymbol will be used as the partition key
        String partitionKey = String.valueOf(orderEntryRequest.getOrderType());
        String TOPIC = "jtradebot-processor-orders";
        kafkaTemplate.send(TOPIC, partitionKey, orderEntryRequest);
    }

}
