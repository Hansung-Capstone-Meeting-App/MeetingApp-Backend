package com.capston.demo.domain.meeting.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * STT 결과 저장 요청 DTO
 * 엔티티: MeetingTranscript + TranscriptSegment
 */
@Getter
@Setter
public class TranscriptRequest {

    /** meeting_transcripts.recording_id - 어떤 녹음 파일을 분석했는지 (meeting_recordings.id 참조) */
    private Long recordingId;

    /** meeting_transcripts.full_text - STT 변환된 전체 텍스트 */
    private String fullText;

    /** meeting_transcripts.summary - Gemini API로 생성한 회의 요약 */
    private String summary;

    /** meeting_transcripts.keywords - 핵심 키워드 JSON 배열 예) ["예산", "마감일"] */
    private String keywords;

    /** transcript_segments - 화자별 발화 세그먼트 목록 */
    private List<SegmentRequest> segments;

    /**
     * 화자별 발화 세그먼트 요청
     * 엔티티: TranscriptSegment
     */
    @Getter
    @Setter
    public static class SegmentRequest {

        /** transcript_segments.speaker_label - STT가 부여한 화자 레이블 예) "SPEAKER_01" */
        private String speakerLabel;

        /** transcript_segments.user_id - 화자 매핑 후 실제 유저 ID (nullable) */
        private Long userId;

        /** transcript_segments.content - 해당 화자의 발화 내용 */
        private String content;

        /** transcript_segments.start_sec - 발화 시작 시각 (초 단위) */
        private Float startSec;

        /** transcript_segments.end_sec - 발화 종료 시각 (초 단위) */
        private Float endSec;

        /** transcript_segments.sequence_order - 발화 순서 번호 */
        private Integer sequence;
    }
}
