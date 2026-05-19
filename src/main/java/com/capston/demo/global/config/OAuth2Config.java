package com.capston.demo.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class OAuth2Config {

    /**
     * 기본 SimpleClientHttpRequestFactory(HttpURLConnection)는 PATCH 미지원.
     * Notion API(블록 추가·페이지 수정)에 PATCH가 필요하므로 JdkClientHttpRequestFactory 사용.
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate(new JdkClientHttpRequestFactory());
    }
}
