package com.jtradebot.processor.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableMongoRepositories(basePackages = {"com.jtradebot.processor.repository", "com.jtradebot.tickstore.repository"})
@Slf4j
public class MongoConfig extends AbstractMongoClientConfiguration {

    @Value("${spring.data.mongodb.uri}")
    private String mongoUri;

    @Value("${spring.data.mongodb.database}")
    private String databaseName;

    @Override
    protected String getDatabaseName() {
        log.info("MongoDB Database Name: {}", databaseName);
        return databaseName;
    }

    @Override
    @Bean
    public MongoClient mongoClient() {
        log.info("Creating MongoDB client with URI: {}", mongoUri.replaceAll(":[^:@]*@", ":****@"));
        log.info("🚀 FORCE DEPLOYMENT TEST - MongoDB client initialization at: {}", java.time.LocalDateTime.now());
        return MongoClients.create(mongoUri);
    }
}
