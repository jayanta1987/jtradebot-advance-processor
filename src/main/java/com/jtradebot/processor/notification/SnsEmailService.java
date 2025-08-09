package com.jtradebot.processor.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SnsEmailService {

    private final SnsClient snsClient;

    @Value("${aws.sns.topic-arn}")
    private String snsTopicArn;
    @Async
    public void sendEmail(String subject, String message) {
        // Build the PublishRequest
        PublishRequest publishRequest = PublishRequest.builder()
                .topicArn(snsTopicArn)
                .subject(subject)
                .message(message)
                .build();

        // Publish the message
        PublishResponse response = snsClient.publish(publishRequest);
    }
}
