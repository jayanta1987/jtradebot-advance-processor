package com.jtradebot.processor.config;

import com.jtradebot.processor.aws.AwsSecretHandler;
import com.zerodhatech.kiteconnect.KiteConnect;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class KiteConnectConfig {

    private final AwsSecretHandler awsSecretHandler;

    @Value("${aws.kite.api-key}")
    private String kiteApiKey;

    @Value("${aws.kite.secret-name}")
    private String kiteConnectAwsSecretName;

    @Bean
    public KiteConnect kiteConnect() {
        log.info("Creating KiteConnect instance....");
        return new KiteConnect(awsSecretHandler.getSecretMap(kiteConnectAwsSecretName).get(kiteApiKey));
    }



}