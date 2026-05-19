package com.capston.demo.domain.recording.dto.response;

/**
 * 녹음 파이프라인 표시용 단계(프론트 폴링·UI 문구용).
 * {@link com.capston.demo.domain.meeting.entity.RecordingStatus} 와 별도로,
 * 트랜스크립트·매핑·분석 여부를 조합해 계산(computed, DB enum 확장 없이)한다.
 */
public enum RecordingPipelinePhase {
    /** 업로드 진행 중 (드물게만 노출) */
    UPLOADING,
    /** 업로드 완료, 전사 전 */
    UPLOADED,
    /** 전사(STT) 진행 중 — 트랜스크립트 문서가 아직 없음 */
    TRANSCRIBING,
    /** 전사 완료 후 필수 화자 매핑이 덜 됨 */
    AWAITING_SPEAKER_MAPPING,
    /** 매핑 완료, Gemini 분석 호출 가능 */
    READY_FOR_ANALYSIS,
    /** Gemini까지 완료({@code analyzedAt} 설정됨) */
    COMPLETE,
    FAILED
}
