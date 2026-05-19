package com.capston.demo.domain.meeting.service;

import com.capston.demo.domain.meeting.dto.export.MeetingExportReportModel;
import com.capston.demo.domain.meeting.dto.export.MeetingExportReportModel.EventRow;
import com.capston.demo.domain.meeting.dto.export.MeetingExportReportModel.TaskRow;
import com.capston.demo.global.exception.BusinessException;
import com.capston.demo.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 회의 리포트({@link MeetingExportReportModel})를 Notion 데이터베이스 페이지로 export.
 * DB 속성: Name(title), Date(date) — 캘린더 연동과 동일 컨벤션.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotionMeetingNotesService {

    private static final String NOTION_PAGES_URL = "https://api.notion.com/v1/pages";
    private static final String NOTION_BLOCKS_URL = "https://api.notion.com/v1/blocks";
    private static final DateTimeFormatter NOTION_DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final int MAX_TEXT_LEN = 1900;

    private final RestTemplate restTemplate;

    @Value("${spring.security.oauth2.client.provider.notion.notion-version:2022-06-28}")
    private String notionVersion;

    /**
     * @param existingPageId null이면 DB에 새 행 생성, 있으면 해당 페이지 속성·본문 갱신
     * @return 생성·갱신된 Notion 페이지 ID
     */
    public String exportMeetingNotes(MeetingExportReportModel model,
                                     String accessToken,
                                     String databaseId,
                                     String existingPageId,
                                     LocalDateTime meetingCreatedAt) {
        try {
            HttpHeaders headers = notionHeaders(accessToken);
            String pageId;
            if (StringUtils.hasText(existingPageId)) {
                pageId = existingPageId;
                patchPageProperties(pageId, model, meetingCreatedAt, headers);
                replacePageContent(pageId, model, headers);
            } else {
                pageId = createPageInDatabase(model, accessToken, databaseId, meetingCreatedAt, headers);
                appendPageContent(pageId, model, headers);
            }
            return pageId;
        } catch (BusinessException e) {
            throw e;
        } catch (HttpClientErrorException e) {
            log.error("Notion meeting notes API error: status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new BusinessException(ErrorCode.NOTION_MEETING_NOTES_EXPORT_FAILED, e);
        } catch (Exception e) {
            log.error("Notion meeting notes export error: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.NOTION_MEETING_NOTES_EXPORT_FAILED, e);
        }
    }

    public static String toNotionPageUrl(String pageId) {
        if (!StringUtils.hasText(pageId)) {
            return null;
        }
        return "https://www.notion.so/" + pageId.replace("-", "");
    }

    private String createPageInDatabase(MeetingExportReportModel model,
                                        String accessToken,
                                        String databaseId,
                                        LocalDateTime meetingCreatedAt,
                                        HttpHeaders headers) {
        Map<String, Object> body = new HashMap<>();
        body.put("parent", Map.of("database_id", databaseId));
        body.put("properties", buildProperties(model, meetingCreatedAt));

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                NOTION_PAGES_URL,
                HttpMethod.POST,
                request,
                new ParameterizedTypeReference<>() {}
        );
        return extractPageId(response);
    }

    private void patchPageProperties(String pageId,
                                     MeetingExportReportModel model,
                                     LocalDateTime meetingCreatedAt,
                                     HttpHeaders headers) {
        Map<String, Object> body = Map.of("properties", buildProperties(model, meetingCreatedAt));
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        restTemplate.exchange(
                NOTION_PAGES_URL + "/" + pageId,
                HttpMethod.PATCH,
                request,
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );
    }

    private void replacePageContent(String pageId, MeetingExportReportModel model, HttpHeaders headers) {
        deleteAllBlockChildren(pageId, headers);
        appendPageContent(pageId, model, headers);
    }

    private void deleteAllBlockChildren(String pageId, HttpHeaders headers) {
        String url = NOTION_BLOCKS_URL + "/" + pageId + "/children?page_size=100";
        while (url != null) {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );
            Map<String, Object> body = response.getBody();
            if (body == null) {
                break;
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> results = (List<Map<String, Object>>) body.get("results");
            if (results != null) {
                for (Map<String, Object> block : results) {
                    Object id = block.get("id");
                    if (id instanceof String blockId) {
                        restTemplate.exchange(
                                NOTION_BLOCKS_URL + "/" + blockId,
                                HttpMethod.DELETE,
                                new HttpEntity<>(headers),
                                Void.class
                        );
                    }
                }
            }
            url = body.get("next_cursor") != null && Boolean.TRUE.equals(body.get("has_more"))
                    ? NOTION_BLOCKS_URL + "/" + pageId + "/children?page_size=100&start_cursor=" + body.get("next_cursor")
                    : null;
        }
    }

    private void appendPageContent(String pageId, MeetingExportReportModel model, HttpHeaders headers) {
        List<Map<String, Object>> blocks = buildContentBlocks(model);
        if (blocks.isEmpty()) {
            return;
        }
        Map<String, Object> body = Map.of("children", blocks);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        restTemplate.exchange(
                NOTION_BLOCKS_URL + "/" + pageId + "/children",
                HttpMethod.PATCH,
                request,
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );
    }

    private List<Map<String, Object>> buildContentBlocks(MeetingExportReportModel model) {
        List<Map<String, Object>> blocks = new ArrayList<>();
        blocks.add(heading2("요약"));
        blocks.add(paragraph(StringUtils.hasText(model.getSummary()) ? model.getSummary() : "-"));

        if (model.getKeywords() != null && !model.getKeywords().isEmpty()) {
            blocks.add(heading2("키워드"));
            for (String keyword : model.getKeywords()) {
                if (StringUtils.hasText(keyword)) {
                    blocks.add(bullet(keyword.trim()));
                }
            }
        }

        blocks.add(heading2("할 일"));
        blocks.add(paragraph(String.format("전체 %d · 할 일 %d · 진행 중 %d · 완료 %d",
                model.getTaskTotal(), model.getTaskTodo(), model.getTaskInProgress(), model.getTaskDone())));
        if (model.getTasks() != null) {
            for (TaskRow task : model.getTasks()) {
                String line = String.format("[%s] %s · 담당: %s · 마감: %s",
                        task.getStatusLabel(), task.getTitle(), task.getAssigneeName(), task.getDueDate());
                if (StringUtils.hasText(task.getDescription())) {
                    line += " — " + task.getDescription();
                }
                blocks.add(bullet(line));
            }
        }

        if (model.isIncludeEvents() && model.getEvents() != null && !model.getEvents().isEmpty()) {
            blocks.add(heading2("일정"));
            for (EventRow event : model.getEvents()) {
                blocks.add(bullet(String.format("%s · %s · %s",
                        event.getTitle(), event.getPeriod(), event.getAssigneeName())));
            }
        }

        blocks.add(paragraph("MeetingApp에서보냄 · " + model.getExportedAt()));
        return blocks;
    }

    private Map<String, Object> buildProperties(MeetingExportReportModel model, LocalDateTime meetingCreatedAt) {
        Map<String, Object> properties = new HashMap<>();
        String title = StringUtils.hasText(model.getMeetingTitle()) ? model.getMeetingTitle() : "회의";
        properties.put("Name", Map.of(
                "title", List.of(Map.of(
                        "type", "text",
                        "text", Map.of("content", truncate(title))
                ))
        ));

        LocalDateTime dateTime = meetingCreatedAt != null ? meetingCreatedAt : LocalDateTime.now();
        Map<String, Object> date = new HashMap<>();
        date.put("start", dateTime.format(NOTION_DATE_FMT));
        properties.put("Date", Map.of("date", date));
        return properties;
    }

    private HttpHeaders notionHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        headers.set("Notion-Version", notionVersion);
        return headers;
    }

    private String extractPageId(ResponseEntity<Map<String, Object>> response) {
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            Object id = response.getBody().get("id");
            if (id instanceof String pageId) {
                return pageId;
            }
        }
        throw new BusinessException(ErrorCode.NOTION_MEETING_NOTES_EXPORT_FAILED);
    }

    private Map<String, Object> heading2(String text) {
        return Map.of(
                "object", "block",
                "type", "heading_2",
                "heading_2", Map.of("rich_text", richText(text))
        );
    }

    private Map<String, Object> paragraph(String text) {
        return Map.of(
                "object", "block",
                "type", "paragraph",
                "paragraph", Map.of("rich_text", richText(text))
        );
    }

    private Map<String, Object> bullet(String text) {
        return Map.of(
                "object", "block",
                "type", "bulleted_list_item",
                "bulleted_list_item", Map.of("rich_text", richText(text))
        );
    }

    private List<Map<String, Object>> richText(String content) {
        return List.of(Map.of(
                "type", "text",
                "text", Map.of("content", truncate(content != null ? content : ""))
        ));
    }

    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.length() <= MAX_TEXT_LEN) {
            return trimmed;
        }
        return trimmed.substring(0, MAX_TEXT_LEN) + "…";
    }
}
