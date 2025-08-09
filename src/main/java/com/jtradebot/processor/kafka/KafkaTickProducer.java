package com.jtradebot.processor.kafka;

import com.jtradebot.processor.model.CpDetails;
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
    public void sendTickDetails(Tick tick, CpDetails cpDetails) {
        CalculatedTick calculatedTick = toCalculatedTick(tick, cpDetails);
        String partitionKey = String.valueOf(tick.getInstrumentToken());
        String TOPIC = "jtradebot-kiteconnect-ticks";
        kafkaTemplate.send(TOPIC, partitionKey, calculatedTick);
    }

}
