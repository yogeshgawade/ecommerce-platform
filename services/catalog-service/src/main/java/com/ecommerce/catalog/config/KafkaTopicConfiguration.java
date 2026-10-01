package com.ecommerce.catalog.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfiguration {

    @Bean
    @ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
    NewTopic catalogEventsTopic(
            @Value("${app.kafka.product-topic:catalog-events}") String productTopic
    ) {
        return TopicBuilder.name(productTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
