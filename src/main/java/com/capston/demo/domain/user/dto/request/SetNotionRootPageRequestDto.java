package com.capston.demo.domain.user.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * GET /notion/root-pages 에서 선택한 workspace 최상위 page ID 저장.
 */
@Getter
@Setter
@NoArgsConstructor
public class SetNotionRootPageRequestDto {

    /** Meetflow 캘린더·회의록 DB를 둘 Notion page ID */
    private String parentPageId;
}
