package com.ecommerce.order.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class HttpClientConfiguration {
    @Bean
    RestClient catalogRestClient(@Value("${app.catalog.base-url:http://localhost:8081}") String catalogBaseUrl) {
        return RestClient.builder().baseUrl(catalogBaseUrl).build();
    }
}
