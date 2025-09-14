package com.jtradebot.processor.kafka;

import com.jtradebot.tickstore.repository.CalculatedTick;
import com.zerodhatech.models.Tick;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import static com.jtradebot.processor.mapper.TickMapper.toCalculatedTick;

@Service
@RequiredArgsConstructor
@Slf4j
//@Profile("!local")
public class KafkaTickProducer {

    private final KafkaTemplate<String, CalculatedTick> kafkaTemplate;

    @Async
    public void sendTickDetails(Tick tick) {
        try {
            CalculatedTick calculatedTick = toCalculatedTick(tick);
            String partitionKey = String.valueOf(tick.getInstrumentToken());
            String TOPIC = "jtrade_advance_ticks";
            
            log.debug("Sending tick to Kafka topic: {} with key: {}", TOPIC, partitionKey);
            kafkaTemplate.send(TOPIC, partitionKey, calculatedTick)
                .whenComplete((result, failure) -> {
                    if (failure != null) {
                        log.error("Failed to send tick to Kafka topic: {} for instrument: {}. Error: {}", 
                            TOPIC, tick.getInstrumentToken(), failure.getMessage(), failure);
                    } else {
                        log.debug("Successfully sent tick to Kafka topic: {} partition: {} offset: {}", 
                            TOPIC, result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                    }
                });
        } catch (Exception e) {
            log.error("Exception occurred while sending tick to Kafka for instrument: {}. Error: {}", 
                tick.getInstrumentToken(), e.getMessage(), e);
        }
    }

}
