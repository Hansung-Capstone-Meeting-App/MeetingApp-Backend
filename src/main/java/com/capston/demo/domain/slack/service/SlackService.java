package com.capston.demo.domain.slack.service;

import com.capston.demo.domain.ai.dto.response.GeminiAnalyzeResponse;
import com.capston.demo.domain.ai.dto.response.TranscribeResponse;
import com.capston.demo.domain.ai.service.MeetingAnalysisService;
import com.capston.demo.domain.meeting.entity.Meeting;
import com.capston.demo.domain.meeting.entity.MeetingTranscript;
import com.capston.demo.domain.meeting.repository.MeetingRepository;
import com.capston.demo.domain.meeting.repository.MeetingTranscriptMongoRepository;
import com.capston.demo.domain.recording.dto.response.RecordingResponse;
import com.capston.demo.domain.recording.service.RecordingService;
import com.capston.demo.domain.user.entity.User;
import com.capston.demo.domain.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.response.files.FilesInfoResponse;
import com.slack.api.methods.response.users.UsersInfoResponse;
import com.slack.api.model.block.Blocks;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.composition.BlockCompositions;
import com.slack.api.model.block.element.BlockElements;
import com.slack.api.model.view.Views;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SlackService {

    private final UserRepository userRepository;
    private final MeetingRepository meetingRepository;
    private final RecordingService recordingService;
    private final MeetingAnalysisService meetingAnalysisService;
    private final MeetingTranscriptMongoRepository transcriptRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${slack.bot-token}")
    private String botToken;

    private static final List<String> ALLOWED_MIMETYPES = List.of(
            "audio/mp4", "audio/mpeg", "audio/m4a", "audio/x-m4a"
    );

    // STT 완료 후 화자 매핑 대기 중인 분석 정보 (key: transcriptId)
    private final ConcurrentHashMap<String, PendingAnalysis> pendingAnalyses = new ConcurrentHashMap<>();

    @Getter
    public static class PendingAnalysis {
        private final String transcriptId;
        private final Long userId;
        private final String channelId;
        private final String meetingTitle;
        private final List<String> speakerLabels;

        public PendingAnalysis(String transcriptId, Long userId, String channelId,
                               String meetingTitle, List<String> speakerLabels) {
            this.transcriptId = transcriptId;
            this.userId = userId;
            this.channelId = channelId;
            this.meetingTitle = meetingTitle;
            this.speakerLabels = speakerLabels;
        }
    }

    /**
     * Slack file_shared 이벤트 처리 진입점.
     * 컨트롤러에서 호출 → @Async로 즉시 반환 (3초 제한 대응)
     */
    @Async
    public void handleFileShared(String slackFileId, String slackUserId, String channelId) {
        try {
            MethodsClient methods = Slack.getInstance().methods(botToken);

            // 1. 파일 정보 조회
            FilesInfoResponse fileInfo = methods.filesInfo(r -> r.file(slackFileId));
            if (!fileInfo.isOk()) {
                log.warn("files.info 실패. fileId={}, error={}", slackFileId, fileInfo.getError());
                return;
            }
            com.slack.api.model.File slackFile = fileInfo.getFile();

            // 2. 오디오 파일 여부 확인
            String mimetype = slackFile.getMimetype();
            if (!ALLOWED_MIMETYPES.contains(mimetype)) {
                log.info("오디오 파일이 아닌 파일 공유 무시. fileId={}, mimetype={}", slackFileId, mimetype);
                return;
            }

            // 3. 사용자 이메일 조회
            UsersInfoResponse userInfo = methods.usersInfo(r -> r.user(slackUserId));
            if (!userInfo.isOk()) {
                log.warn("users.info 실패. slackUserId={}, error={}", slackUserId, userInfo.getError());
                return;
            }
            String email = userInfo.getUser().getProfile().getEmail();

            // 4. DB에서 User 조회 (없으면 자동 생성)
            User user = resolveUser(email, slackUserId);

            // 5. Meeting 자동 생성
            Meeting meeting = createMeeting(user, slackFile.getName());

            // 6. Slack 파일 → S3 업로드
            String downloadUrl = slackFile.getUrlPrivateDownload();
            long fileSize = slackFile.getSize();
            String filename = sanitizeFilename(slackFile.getName());

            RecordingResponse recording = downloadAndUpload(meeting.getId(), downloadUrl, fileSize, filename);

            // 7. STT (AssemblyAI)
            TranscribeResponse transcribeResponse = meetingAnalysisService.transcribe(
                    meeting.getId(), recording.getRecordingId(), user.getId()
            );

            // 8. 고유 화자 레이블 추출
            List<String> speakerLabels = transcribeResponse.getSegments().stream()
                    .map(TranscribeResponse.SegmentInfo::getSpeakerLabel)
                    .distinct()
                    .collect(Collectors.toList());

            // 9. PendingAnalysis 저장 (화자 매핑 완료 시 Gemini 분석에 사용)
            String transcriptId = transcribeResponse.getTranscriptId();
            pendingAnalyses.put(transcriptId, new PendingAnalysis(
                    transcriptId, user.getId(), channelId, meeting.getTitle(), speakerLabels
            ));

            // 10. 채널에 화자 매핑 버튼 메시지 게시
            postMappingButtonMessage(methods, channelId, meeting.getTitle(), transcriptId, speakerLabels);

        } catch (Exception e) {
            log.error("Slack 파일 처리 중 오류 발생. fileId={}, userId={}", slackFileId, slackUserId, e);
            try {
                Slack.getInstance().methods(botToken).chatPostMessage(r -> r
                        .channel(channelId)
                        .text("회의 파일 분석 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.")
                );
            } catch (Exception ex) {
                log.warn("오류 알림 채널 전송 실패. channelId={}", channelId, ex);
            }
        }
    }

    /**
     * 버튼 클릭 시 화자 매핑 Modal 열기.
     * trigger_id 유효시간(3초)이 짧으므로 동기 호출.
     */
    public void openSpeakerMappingModal(String triggerId, String transcriptId) {
        PendingAnalysis pending = pendingAnalyses.get(transcriptId);
        if (pending == null) {
            log.warn("PendingAnalysis 없음. transcriptId={}", transcriptId);
            return;
        }

        try {
            MethodsClient methods = Slack.getInstance().methods(botToken);

            List<LayoutBlock> blocks = new ArrayList<>();
            for (String label : pending.getSpeakerLabels()) {
                String blockId = "speaker_" + label;
                String actionId = "user_select_" + label;
                blocks.add(
                    Blocks.input(i -> i
                        .blockId(blockId)
                        .label(BlockCompositions.plainText(label))
                        .element(BlockElements.usersSelect(u -> u
                            .actionId(actionId)
                            .placeholder(BlockCompositions.plainText("멤버 선택"))
                        ))
                    )
                );
            }

            methods.viewsOpen(r -> r
                .triggerId(triggerId)
                .view(Views.view(v -> v
                    .type("modal")
                    .callbackId("speaker_mapping_modal")
                    .privateMetadata(transcriptId)
                    .title(Views.viewTitle(t -> t.type("plain_text").text("화자 매핑")))
                    .submit(Views.viewSubmit(s -> s.type("plain_text").text("제출")))
                    .close(Views.viewClose(c -> c.type("plain_text").text("취소")))
                    .blocks(blocks)
                ))
            );
        } catch (Exception e) {
            log.error("Modal 열기 실패. triggerId={}, transcriptId={}", triggerId, transcriptId, e);
        }
    }

    /**
     * Modal 제출 처리 — 화자 매핑 저장 → Gemini 분석 → 채널 결과 게시.
     */
    @Async
    public void handleSpeakerMappingSubmit(String transcriptId, JsonNode values) {
        PendingAnalysis pending = pendingAnalyses.remove(transcriptId);
        if (pending == null) {
            log.warn("PendingAnalysis 없음. transcriptId={}", transcriptId);
            return;
        }

        try {
            MethodsClient methods = Slack.getInstance().methods(botToken);

            // 1. 화자 매핑 MongoDB 저장
            MeetingTranscript transcript = transcriptRepository.findById(transcriptId)
                    .orElseThrow(() -> new IllegalArgumentException("트랜스크립트 없음. id=" + transcriptId));

            for (String label : pending.getSpeakerLabels()) {
                String blockId = "speaker_" + label;
                String actionId = "user_select_" + label;
                String selectedSlackUserId = values.path(blockId).path(actionId).path("selected_user").asText(null);
                if (selectedSlackUserId == null) continue;

                // Slack에서 표시 이름 조회
                String userName = selectedSlackUserId;
                try {
                    UsersInfoResponse userInfo = methods.usersInfo(r -> r.user(selectedSlackUserId));
                    if (userInfo.isOk()) {
                        String displayName = userInfo.getUser().getProfile().getDisplayName();
                        userName = (displayName != null && !displayName.isBlank())
                                ? displayName
                                : userInfo.getUser().getProfile().getRealName();
                    }
                } catch (Exception e) {
                    log.warn("users.info 조회 실패. slackUserId={}", selectedSlackUserId, e);
                }

                final String finalSlackUserId = selectedSlackUserId;
                final String finalUserName = userName;

                MeetingTranscript.SpeakerMappingEmbedded existing = transcript.getSpeakerMappings().stream()
                        .filter(m -> m.getSpeakerLabel().equals(label))
                        .findFirst().orElse(null);

                if (existing != null) {
                    existing.setSlackUserId(finalSlackUserId);
                    existing.setUserName(finalUserName);
                } else {
                    MeetingTranscript.SpeakerMappingEmbedded mapping = new MeetingTranscript.SpeakerMappingEmbedded();
                    mapping.setSpeakerLabel(label);
                    mapping.setSlackUserId(finalSlackUserId);
                    mapping.setUserName(finalUserName);
                    transcript.getSpeakerMappings().add(mapping);
                }
            }
            transcriptRepository.save(transcript);

            // 2. Gemini AI 분석
            GeminiAnalyzeResponse analyzeResponse = meetingAnalysisService.geminiAnalyze(
                    transcriptId, pending.getUserId()
            );

            // 3. 결과 채널 게시
            postToChannel(methods, pending.getChannelId(), pending.getMeetingTitle(), analyzeResponse);

        } catch (Exception e) {
            log.error("화자 매핑 처리 중 오류. transcriptId={}", transcriptId, e);
            try {
                Slack.getInstance().methods(botToken).chatPostMessage(r -> r
                        .channel(pending.getChannelId())
                        .text("회의 분석 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.")
                );
            } catch (Exception ex) {
                log.warn("오류 알림 전송 실패. channelId={}", pending.getChannelId(), ex);
            }
        }
    }

    protected User resolveUser(String email, String slackUserId) {
        return userRepository.findByEmail(email).map(user -> {
            if (user.getSlackUserId() == null) {
                user.setSlackUserId(slackUserId);
                userRepository.save(user);
            }
            return user;
        }).orElseGet(() -> {
            User newUser = new User();
            newUser.setEmail(email);
            newUser.setName(email.split("@")[0]);
            newUser.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
            newUser.setSlackUserId(slackUserId);
            return userRepository.save(newUser);
        });
    }

    protected Meeting createMeeting(User user, String filename) {
        Meeting meeting = new Meeting();
        meeting.setTitle(filename != null ? filename : "Slack 공유 회의");
        meeting.setCreatedBy(user.getId());
        return meetingRepository.save(meeting);
    }

    private RecordingResponse downloadAndUpload(Long meetingId, String downloadUrl,
                                                long fileSize, String filename) throws Exception {
        URL url = new URL(downloadUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("Authorization", "Bearer " + botToken);
        conn.connect();

        try (InputStream inputStream = conn.getInputStream()) {
            return recordingService.uploadFromStream(meetingId, inputStream, fileSize, filename);
        } finally {
            conn.disconnect();
        }
    }

    private void postMappingButtonMessage(MethodsClient methods, String channelId,
                                          String meetingTitle, String transcriptId,
                                          List<String> speakerLabels) {
        try {
            String speakerList = String.join(", ", speakerLabels);
            List<LayoutBlock> blocks = List.of(
                Blocks.section(s -> s.text(BlockCompositions.markdownText(
                    String.format("*STT 완료!* _%s_\n감지된 화자: %s\n화자를 워크스페이스 멤버와 매핑해주세요.", meetingTitle, speakerList)
                ))),
                Blocks.actions(a -> a
                    .blockId("speaker_mapping_actions")
                    .elements(List.of(
                        BlockElements.button(b -> b
                            .text(BlockCompositions.plainText("화자 매핑하기"))
                            .actionId("open_speaker_mapping")
                            .value(transcriptId)
                        )
                    ))
                )
            );
            methods.chatPostMessage(r -> r.channel(channelId).blocks(blocks));
        } catch (Exception e) {
            log.warn("화자 매핑 버튼 메시지 전송 실패. channelId={}", channelId, e);
        }
    }

    private void postToChannel(MethodsClient methods, String channelId,
                               String meetingTitle, GeminiAnalyzeResponse analysis) {
        try {
            String keywords = analysis.getKeywords() != null
                    ? String.join(", ", analysis.getKeywords())
                    : "없음";

            String message = String.format(
                    "*[회의 분석 완료]* _%s_\n\n*요약*\n%s\n\n*키워드*: %s\n*생성된 태스크*: %d건 | *생성된 이벤트*: %d건",
                    meetingTitle,
                    analysis.getSummary(),
                    keywords,
                    analysis.getSavedTaskCount(),
                    analysis.getSavedEventCount()
            );

            methods.chatPostMessage(r -> r.channel(channelId).text(message));
        } catch (Exception e) {
            log.warn("채널 메시지 전송 실패. channelId={}", channelId, e);
        }
    }

    private String sanitizeFilename(String name) {
        if (name == null) return "recording.m4a";
        String lower = name.toLowerCase();
        if (lower.endsWith(".m4a") || lower.endsWith(".mp3")) return name;
        return name + ".m4a";
    }
}
