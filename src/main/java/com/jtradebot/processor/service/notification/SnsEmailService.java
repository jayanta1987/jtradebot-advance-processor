package com.jtradebot.processor.service.notification;

import com.jtradebot.processor.common.ProfileUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

@Service
@RequiredArgsConstructor
@Slf4j
public class SnsEmailService {

    private final SnsClient snsClient;
    private final Environment environment;

    @Value("${aws.sns.topic-arn}")
    private String snsTopicArn;
    @Async
    public void sendEmail(String subject, String message) {
        // Skip email sending for local profile
        if (ProfileUtil.isProfileActive(environment, "local")) {
            log.info("📧 Email notification skipped for local profile - Subject: {}", subject);
            return;
        }
        
        try {
            // Build the PublishRequest
            PublishRequest publishRequest = PublishRequest.builder()
                    .topicArn(snsTopicArn)
                    .subject(subject)
                    .message(message)
                    .build();

            // Publish the message
            snsClient.publish(publishRequest);
            log.info("📧 Email notification sent successfully - Subject: {}", subject);
        } catch (Exception e) {
            log.error("❌ Failed to send email notification - Subject: {}", subject, e);
        }
    }
}
