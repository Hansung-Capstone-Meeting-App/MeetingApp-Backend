package com.capston.demo.domain.calender.service;

import com.capston.demo.domain.calender.entity.Event;
import com.capston.demo.global.exception.BusinessException;
import com.capston.demo.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

// Event 정보를 기반으로 Notion 캘린더(데이터베이스)에 페이지를 생성하는 서비스
@Service
@RequiredArgsConstructor
@Slf4j
public class NotionCalendarService {

    // HTTP 요청을 보내기 위한 Spring의 RestTemplate
    private final RestTemplate restTemplate;

    // 사용할 Notion API 버전 (요청 헤더에 넣어야 함)
    @Value("${spring.security.oauth2.client.provider.notion.notion-version:2022-06-28}")
    private String notionVersion;

    // Notion 페이지 생성 REST API 엔드포인트
    private static final String NOTION_PAGES_URL = "https://api.notion.com/v1/pages";

    /**
     * Event 엔티티 정보를 기반으로 Notion 캘린더(데이터베이스)에 일정을 생성한다.
     *
     * @param event       생성할 이벤트
     * @param accessToken Notion OAuth 액세스 토큰
     * @param databaseId  일정을 생성할 노션 데이터베이스 ID (유저별로 등록한 값)
     * @return 생성된 Notion 페이지 ID
     */
    public String createEventInNotion(Event event, String accessToken, String databaseId) { //Event 엔티티를 기반으로 Notion 캘린더에 일정을 생성
        try {
            // 요청 헤더 구성 (JSON, Bearer 토큰, Notion-Version)
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);
            headers.set("Notion-Version", notionVersion);

            // Event 엔티티를 Notion 페이지 생성 요청 바디로 변환
            Map<String, Object> body = buildNotionPageRequest(event, databaseId);

            // 최종 HTTP 요청 엔티티
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            // Notion 페이지 생성 API 호출
            //RestTemplate 로 POST https://api.notion.com/v1/pages 호출
            ResponseEntity<Map> response = restTemplate.exchange(
                    NOTION_PAGES_URL,
                    HttpMethod.POST,
                    requestEntity,
                    Map.class
            );

            // 성공 응답이며 바디가 존재하면, 생성된 페이지의 id 를 추출
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object id = response.getBody().get("id");
                if (id instanceof String) {
                    return (String) id;
                }
            }

            // 이 시점까지 오면 실패로 간주
            log.warn("Failed to create Notion event. status={}, body={}", response.getStatusCode(), response.getBody());
            throw new BusinessException(ErrorCode.NOTION_EVENT_CREATE_FAILED);
        } catch (BusinessException e) {
            throw e;
        } catch (HttpClientErrorException e) {
            log.error("Notion API error while creating event: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new BusinessException(ErrorCode.NOTION_EVENT_CREATE_FAILED, e);
        } catch (Exception e) {
            log.error("Error while creating Notion event: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.NOTION_EVENT_CREATE_FAILED, e);
        }
    }

    // Event 엔티티를 Notion 페이지 생성용 요청 바디(Map 구조)로 변환
    private Map<String, Object> buildNotionPageRequest(Event event, String databaseId) {
        Map<String, Object> body = new HashMap<>(); //Notion 페이지 생성용 요청 바디(Map 구조), 리턴할 Map 생성

        // 어떤 데이터베이스에 페이지를 생성할지 설정 (유저별 등록 DB)
        Map<String, Object> parent = Map.of(
                "database_id", databaseId
        );

        Map<String, Object> properties = new HashMap<>(); //properties 는 Notion 페이지 생성용 요청 바디(Map 구조)의 속성들을 저장할 Map

        // 제목·날짜만 전송 (노션 DB 컬럼 이름이 "Name", "Date"인 경우)
        Map<String, Object> titleText = Map.of(
                "type", "text",
                "text", Map.of("content", event.getTitle())
        );
        properties.put("Name", Map.of(
                "title", new Object[]{titleText}
        ));

        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        Map<String, Object> date = new HashMap<>();
        date.put("start", event.getStartAt().format(formatter)); //시작일시 설정
        date.put("end", event.getEndAt().format(formatter)); //종료일시 설정
        //properties.Date 에 시작/종료 날짜
        properties.put("Date", Map.of("date", date)); // Date 속성에 시작일시와 종료일시 설정

        // 최종 body 에 parent, properties 를 설정
        body.put("parent", parent);
        body.put("properties", properties);
        return body;
    }
}

