package com.jtradebot.processor.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@Slf4j
public class MongoDbStartupConfig implements CommandLineRunner {

    @Value("${spring.data.mongodb.uri:NOT_SET}")
    private String mongoUri;

    @Value("${spring.data.mongodb.database:NOT_SET}")
    private String databaseName;

    @Value("${spring.profiles.active:NOT_SET}")
    private String activeProfile;

    private final Environment environment;

    public MongoDbStartupConfig(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("=== MongoDB Configuration Debug Info ===");
        log.info("Active Profile: {}", activeProfile);
        log.info("MongoDB URI: {}", mongoUri.replaceAll(":[^:@]*@", ":****@"));
        log.info("MongoDB Database: {}", databaseName);
        
        // Check if MongoDB URI is properly set
        if ("NOT_SET".equals(mongoUri)) {
            log.error("MongoDB URI is not set! This will cause connection issues.");
        } else {
            log.info("MongoDB URI is properly configured.");
        }
        
        // Log all active profiles
        String[] profiles = environment.getActiveProfiles();
        log.info("All Active Profiles: {}", String.join(", ", profiles));
        
        log.info("=== End MongoDB Configuration Debug Info ===");
    }
}
