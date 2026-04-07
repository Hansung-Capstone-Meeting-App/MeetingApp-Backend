package com.capston.demo.domain.ai.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.List;

@Getter
@AllArgsConstructor
public class GeminiAnalyzeResponse {
    private String summary;
    private List<String> keywords;
    private int savedTaskCount;
    private int savedEventCount;
}
