package com.jtradebot.processor.config;


import com.jtradebot.processor.model.IntraDayPreference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class DataConfig {

    @Bean
    public IntraDayPreference intraDayPreference() {
        log.info("Creating intraDayPreference instance....");
        return new IntraDayPreference();
    }

}
