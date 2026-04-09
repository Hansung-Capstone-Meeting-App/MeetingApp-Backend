package com.capston.demo.domain.slack.service;

import com.capston.demo.domain.ai.dto.response.GeminiAnalyzeResponse;
import com.capston.demo.domain.ai.dto.response.TranscribeResponse;
import com.capston.demo.domain.ai.service.MeetingAnalysisService;
import com.capston.demo.domain.meeting.entity.Meeting;
import com.capston.demo.domain.meeting.repository.MeetingRepository;
import com.capston.demo.domain.recording.dto.response.RecordingResponse;
import com.capston.demo.domain.recording.service.RecordingService;
import com.capston.demo.domain.user.entity.User;
import com.capston.demo.domain.user.repository.UserRepository;
import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.response.files.FilesInfoResponse;
import com.slack.api.methods.response.users.UsersInfoResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SlackService {

    private final UserRepository userRepository;
    private final MeetingRepository meetingRepository;
    private final RecordingService recordingService;
    private final MeetingAnalysisService meetingAnalysisService;
    private final PasswordEncoder passwordEncoder;

    @Value("${slack.bot-token}")
    private String botToken;

    private static final List<String> ALLOWED_MIMETYPES = List.of(
            "audio/mp4", "audio/mpeg", "audio/m4a", "audio/x-m4a"
    );

    /**
     * Slack file_shared 이벤트 처리 진입점.
     * 컨트롤러에서 호출 → @Async로 즉시 반환 (3초 제한 대응)
     */
    @Async
    public void handleFileShared(String slackFileId, String slackUserId) {
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

            // 8. Gemini AI 분석
            GeminiAnalyzeResponse analyzeResponse = meetingAnalysisService.geminiAnalyze(
                    transcribeResponse.getTranscriptId(), user.getId()
            );

            // 9. 분석 결과 DM 전송
            sendDm(methods, slackUserId, meeting.getTitle(), analyzeResponse);

        } catch (Exception e) {
            log.error("Slack 파일 처리 중 오류 발생. fileId={}, userId={}", slackFileId, slackUserId, e);
            try {
                Slack.getInstance().methods(botToken).chatPostMessage(r -> r
                        .channel(slackUserId)
                        .text("회의 파일 분석 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.")
                );
            } catch (Exception ex) {
                log.warn("오류 알림 DM 전송 실패. slackUserId={}", slackUserId, ex);
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

    private void sendDm(MethodsClient methods, String slackUserId,
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

            methods.chatPostMessage(r -> r.channel(slackUserId).text(message));
        } catch (Exception e) {
            log.warn("DM 전송 실패. slackUserId={}", slackUserId, e);
        }
    }

    private String sanitizeFilename(String name) {
        if (name == null) return "recording.m4a";
        String lower = name.toLowerCase();
        if (lower.endsWith(".m4a") || lower.endsWith(".mp3")) return name;
        return name + ".m4a";
    }
}
