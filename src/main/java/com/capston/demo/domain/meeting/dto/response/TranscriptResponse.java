package com.capston.demo.domain.meeting.dto.response;

import com.capston.demo.domain.meeting.entity.MeetingTranscript;
import com.capston.demo.domain.meeting.entity.TranscriptSegment;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 트랜스크립트 응답 DTO
 * 엔티티: MeetingTranscript (meeting_transcripts 테이블)
 * 화자별 세그먼트 목록(SegmentResponse)을 포함
 */
@Getter
public class TranscriptResponse {

    /** meeting_transcripts.id */
    private final Long id;

    /** meeting_transcripts.meeting_id - 어떤 회의의 트랜스크립트인지 */
    private final Long meetingId;

    /** meeting_transcripts.recording_id - 분석에 사용된 녹음 파일 ID (nullable) */
    private final Long recordingId;

    /** meeting_transcripts.full_text - STT 변환 전체 텍스트 */
    private final String fullText;

    /** meeting_transcripts.summary - Claude API 회의 요약 */
    private final String summary;

    /** meeting_transcripts.keywords - 핵심 키워드 JSON 문자열 예) ["예산","마감일"] */
    private final String keywords;

    /** meeting_transcripts.analyzed_at - AI 분석 완료 시각 */
    private final LocalDateTime analyzedAt;

    /** meeting_transcripts.created_at - 레코드 생성 시각 */
    private final LocalDateTime createdAt;

    /** transcript_segments - 화자별 발화 세그먼트 목록 */
    private final List<SegmentResponse> segments;

    public TranscriptResponse(MeetingTranscript transcript) {
        this.id = transcript.getId();
        this.meetingId = transcript.getMeeting().getId();
        this.recordingId = transcript.getRecording() != null ? transcript.getRecording().getId() : null;
        this.fullText = transcript.getFullText();
        this.summary = transcript.getSummary();
        this.keywords = transcript.getKeywords();
        this.analyzedAt = transcript.getAnalyzedAt();
        this.createdAt = transcript.getCreatedAt();
        this.segments = transcript.getSegments().stream()
                .map(SegmentResponse::new)
                .collect(Collectors.toList());
    }

    /**
     * 화자별 발화 세그먼트 응답
     * 엔티티: TranscriptSegment (transcript_segments 테이블)
     */
    @Getter
    public static class SegmentResponse {

        /** transcript_segments.id */
        private final Long id;

        /** transcript_segments.speaker_label - STT 화자 레이블 예) "SPEAKER_01" */
        private final String speakerLabel;

        /** transcript_segments.user_id - 매핑된 실제 유저 ID (매핑 전이면 null) */
        private final Long userId;

        /** transcript_segments.content - 발화 내용 */
        private final String content;

        /** transcript_segments.start_sec - 발화 시작 시각 (초) */
        private final Float startSec;

        /** transcript_segments.end_sec - 발화 종료 시각 (초) */
        private final Float endSec;

        /** transcript_segments.sequence_order - 발화 순서 번호 */
        private final Integer sequence;

        public SegmentResponse(TranscriptSegment segment) {
            this.id = segment.getId();
            this.speakerLabel = segment.getSpeakerLabel();
            this.userId = segment.getUserId();
            this.content = segment.getContent();
            this.startSec = segment.getStartSec();
            this.endSec = segment.getEndSec();
            this.sequence = segment.getSequence();
        }
    }
}
