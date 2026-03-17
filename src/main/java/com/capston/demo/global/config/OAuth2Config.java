package com.capston.demo.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class OAuth2Config {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
