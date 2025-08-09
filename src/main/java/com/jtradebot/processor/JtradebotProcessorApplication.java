package com.jtradebot.processor;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableCaching
@ComponentScan(basePackages = {"com.jtradebot.processor", "com.jtradebot.tickstore"})
@EnableMongoRepositories(basePackages = {"com.jtradebot.processor.repository", "com.jtradebot.tickstore.repository"})
public class JtradebotProcessorApplication {
    public static void main(String[] args) {
        SpringApplication.run(JtradebotProcessorApplication.class, args);
    }
}
