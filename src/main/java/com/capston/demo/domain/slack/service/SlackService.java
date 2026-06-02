package com.capston.demo.domain.slack.service;

import com.capston.demo.domain.ai.dto.response.GeminiAnalyzeResponse;
import com.capston.demo.domain.ai.dto.response.TranscribeResponse;
import com.capston.demo.domain.ai.service.MeetingAnalysisService;
import com.capston.demo.domain.calender.entity.Event;
import com.capston.demo.domain.calender.entity.Task;
import com.capston.demo.domain.calender.entity.TaskStatus;
import com.capston.demo.domain.calender.repository.EventRepository;
import com.capston.demo.domain.calender.repository.TaskRepository;
import com.capston.demo.domain.meeting.dto.response.MeetingNotionExportResponse;
import com.capston.demo.domain.meeting.entity.Meeting;
import com.capston.demo.domain.meeting.entity.MeetingTranscript;
import com.capston.demo.domain.meeting.repository.MeetingRepository;
import com.capston.demo.domain.meeting.repository.MeetingTranscriptMongoRepository;
import com.capston.demo.domain.meeting.service.MeetingExportService;
import com.capston.demo.domain.recording.dto.response.RecordingResponse;
import com.capston.demo.domain.recording.service.RecordingService;
import com.capston.demo.domain.user.entity.User;
import com.capston.demo.domain.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.slack.api.Slack;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.response.files.FilesInfoResponse;
import com.slack.api.methods.response.users.UsersInfoResponse;
import com.slack.api.model.view.Views;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class SlackService {

    private static final List<String> ALLOWED_MIMETYPES = List.of(
            "audio/mp4", "audio/mpeg", "audio/m4a", "audio/x-m4a"
    );

    private final UserRepository userRepository;
    private final MeetingRepository meetingRepository;
    private final RecordingService recordingService;
    private final MeetingAnalysisService meetingAnalysisService;
    private final MeetingTranscriptMongoRepository transcriptRepository;
    private final TaskRepository taskRepository;
    private final EventRepository eventRepository;
    private final PasswordEncoder passwordEncoder;
    private final MeetingExportService meetingExportService;
    private final SlackMessageService slackMessageService;

    @Value("${slack.bot-token}")
    private String botToken;

    private final ConcurrentHashMap<String, PendingAnalysis> pendingAnalyses = new ConcurrentHashMap<>();

    @Getter
    public static class PendingAnalysis {
        private final String transcriptId;
        private final Long userId;
        private final String channelId;
        private final String meetingTitle;
        private final List<String> speakerLabels;
        private final String rootTs;
        private final String threadTs;
        private final Long meetingId;

        public PendingAnalysis(String transcriptId, Long userId, String channelId, String meetingTitle,
                               List<String> speakerLabels, String rootTs, String threadTs, Long meetingId) {
            this.transcriptId = transcriptId;
            this.userId = userId;
            this.channelId = channelId;
            this.meetingTitle = meetingTitle;
            this.speakerLabels = speakerLabels;
            this.rootTs = rootTs;
            this.threadTs = threadTs;
            this.meetingId = meetingId;
        }
    }

    @Getter
    private static class SlackMappedUser {
        private final Long userId;
        private final String slackUserId;
        private final String displayName;

        private SlackMappedUser(Long userId, String slackUserId, String displayName) {
            this.userId = userId;
            this.slackUserId = slackUserId;
            this.displayName = displayName;
        }
    }

    @Async
    public void handleFileShared(String slackFileId, String slackUserId, String channelId) {
        PendingAnalysis pending = null;
        try {
            MethodsClient methods = Slack.getInstance().methods(botToken);

            FilesInfoResponse fileInfo = methods.filesInfo(r -> r.file(slackFileId));
            if (!fileInfo.isOk()) {
                log.warn("files.info failed. fileId={}, error={}", slackFileId, fileInfo.getError());
                return;
            }

            com.slack.api.model.File slackFile = fileInfo.getFile();
            if (!ALLOWED_MIMETYPES.contains(slackFile.getMimetype())) {
                log.info("Ignored non-audio file. fileId={}, mimetype={}", slackFileId, slackFile.getMimetype());
                return;
            }

            String meetingTitle = sanitizeMeetingTitle(slackFile.getName());
            SlackMessageService.WorkflowMessage workflowMessage =
                    slackMessageService.postWorkflowStarted(channelId, meetingTitle);

            slackMessageService.updateWorkflowStatus(channelId, workflowMessage.rootTs(),
                    new SlackMessageService.WorkflowStatus(
                            meetingTitle,
                            "파일을 확인했습니다",
                            "Slack 업로드 파일 메타데이터를 읽고 사용자와 회의를 연결하는 중입니다.",
                            1,
                            false,
                            null,
                            null,
                            null
                    ));

            UsersInfoResponse userInfo = methods.usersInfo(r -> r.user(slackUserId));
            if (!userInfo.isOk()) {
                throw new IllegalStateException("users.info failed: " + userInfo.getError());
            }

            String email = userInfo.getUser().getProfile().getEmail();
            User user = resolveUser(email, slackUserId);
            Meeting meeting = createMeeting(user, slackFile.getName());

            slackMessageService.updateWorkflowStatus(channelId, workflowMessage.rootTs(),
                    new SlackMessageService.WorkflowStatus(
                            meetingTitle,
                            "음성 파일을 업로드했습니다",
                            "Slack 파일을 내려받아 저장소 파이프라인으로 넘겼습니다. 이제 STT를 수행합니다.",
                            2,
                            false,
                            null,
                            null,
                            null
                    ));

            RecordingResponse recording = downloadAndUpload(
                    meeting.getId(),
                    slackFile.getUrlPrivateDownload(),
                    slackFile.getSize(),
                    sanitizeFilename(slackFile.getName())
            );

            TranscribeResponse transcribeResponse = meetingAnalysisService.transcribe(
                    meeting.getId(),
                    recording.getRecordingId(),
                    user.getId()
            );

            List<String> speakerLabels = transcribeResponse.getSegments().stream()
                    .map(TranscribeResponse.SegmentInfo::getSpeakerLabel)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .toList();

            String transcriptId = transcribeResponse.getTranscriptId();
            pending = new PendingAnalysis(
                    transcriptId,
                    user.getId(),
                    channelId,
                    meeting.getTitle(),
                    speakerLabels,
                    workflowMessage.rootTs(),
                    workflowMessage.threadTs(),
                    meeting.getId()
            );
            pendingAnalyses.put(transcriptId, pending);

            slackMessageService.updateWorkflowStatus(channelId, workflowMessage.rootTs(),
                    new SlackMessageService.WorkflowStatus(
                            meetingTitle,
                            "화자 매핑을 기다리고 있습니다",
                            "샘플 발언을 보고 각 화자를 실제 Slack 멤버와 연결해 주세요.",
                            3,
                            false,
                            speakerLabels.size(),
                            null,
                            null
                    ));

            slackMessageService.postSpeakerMappingPrompt(
                    channelId,
                    workflowMessage.threadTs(),
                    meeting.getTitle(),
                    transcriptId,
                    speakerLabels,
                    transcribeResponse.getSegments()
            );
        } catch (Exception e) {
            log.error("Slack file handling failed. fileId={}, userId={}", slackFileId, slackUserId, e);
            if (pending != null) {
                slackMessageService.postErrorMessage(pending.getChannelId(), pending.getThreadTs(),
                        "회의 파일 분석 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
            } else {
                slackMessageService.postErrorMessage(channelId, null,
                        "회의 파일 분석 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
            }
        }
    }

    public void openSpeakerMappingModal(String triggerId, String transcriptId) {
        PendingAnalysis pending = pendingAnalyses.get(transcriptId);
        if (pending == null) {
            log.warn("PendingAnalysis not found. transcriptId={}", transcriptId);
            return;
        }

        try {
            List<com.slack.api.model.block.LayoutBlock> blocks = pending.getSpeakerLabels().stream()
                    .map(label -> (com.slack.api.model.block.LayoutBlock) com.slack.api.model.block.Blocks.input(i -> i
                            .blockId("speaker_" + label)
                            .label(com.slack.api.model.block.composition.BlockCompositions.plainText("화자 " + label))
                            .element(com.slack.api.model.block.element.BlockElements.usersSelect(u -> u
                                    .actionId("user_select_" + label)
                                    .placeholder(com.slack.api.model.block.composition.BlockCompositions.plainText("Slack 멤버 선택"))
                            ))
                    ))
                    .toList();

            client().viewsOpen(r -> r
                    .triggerId(triggerId)
                    .view(Views.view(v -> v
                            .type("modal")
                            .callbackId("speaker_mapping_modal")
                            .privateMetadata(transcriptId)
                            .title(Views.viewTitle(t -> t.type("plain_text").text("화자 매핑")))
                            .submit(Views.viewSubmit(s -> s.type("plain_text").text("분석 시작")))
                            .close(Views.viewClose(c -> c.type("plain_text").text("취소")))
                            .blocks(blocks)
                    )));
        } catch (Exception e) {
            log.error("Failed to open speaker mapping modal. triggerId={}, transcriptId={}", triggerId, transcriptId, e);
        }
    }

    @Async
    public void handleSpeakerMappingSubmit(String transcriptId, JsonNode values) {
        PendingAnalysis pending = pendingAnalyses.remove(transcriptId);
        if (pending == null) {
            log.warn("PendingAnalysis not found. transcriptId={}", transcriptId);
            return;
        }

        try {
            MeetingTranscript transcript = transcriptRepository.findById(transcriptId)
                    .orElseThrow(() -> new IllegalArgumentException("Transcript not found. id=" + transcriptId));

            for (String label : pending.getSpeakerLabels()) {
                String selectedSlackUserId = values.path("speaker_" + label)
                        .path("user_select_" + label)
                        .path("selected_user")
                        .asText(null);
                if (!StringUtils.hasText(selectedSlackUserId)) {
                    continue;
                }
                SlackMappedUser mappedUser = resolveSlackMappedUser(selectedSlackUserId);
                upsertSpeakerMapping(transcript, label, mappedUser);
            }
            transcriptRepository.save(transcript);

            slackMessageService.updateWorkflowStatus(pending.getChannelId(), pending.getRootTs(),
                    new SlackMessageService.WorkflowStatus(
                            pending.getMeetingTitle(),
                            "화자 매핑을 저장했습니다",
                            "사람별 역할과 발언을 연결했습니다. 이제 요약, 할 일, 일정 추출을 진행합니다.",
                            4,
                            false,
                            pending.getSpeakerLabels().size(),
                            null,
                            null
                    ));

            GeminiAnalyzeResponse analyzeResponse = meetingAnalysisService.geminiAnalyze(transcriptId, pending.getUserId());
            MeetingTranscript analyzedTranscript = transcriptRepository.findById(transcriptId).orElse(transcript);

            List<Task> tasks = taskRepository.findByMeetingId(analyzedTranscript.getMeetingId()).stream()
                    .sorted(taskComparator())
                    .toList();
            List<Event> events = eventRepository.findByMeetingId(analyzedTranscript.getMeetingId()).stream()
                    .sorted(Comparator.comparing(Event::getStartAt, Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();

            MeetingNotionExportResponse notionExport = tryExportToNotion(pending);

            slackMessageService.updateWorkflowStatus(pending.getChannelId(), pending.getRootTs(),
                    new SlackMessageService.WorkflowStatus(
                            pending.getMeetingTitle(),
                            "결과 정리가 완료되었습니다",
                            "요약, 할 일, 일정, PDF를 같은 스레드 안에서 확인할 수 있습니다.",
                            5,
                            true,
                            pending.getSpeakerLabels().size(),
                            tasks.size(),
                            events.size()
                    ));

            slackMessageService.postFinalResult(
                    pending.getChannelId(),
                    pending.getThreadTs(),
                    analyzeResponse,
                    tasks,
                    events,
                    analyzedTranscript.getSpeakerMappings(),
                    notionExport
            );
            slackMessageService.uploadPdfsToThread(
                    pending.getChannelId(),
                    pending.getThreadTs(),
                    pending.getMeetingId(),
                    pending.getUserId(),
                    pending.getMeetingTitle()
            );
        } catch (Exception e) {
            log.error("Speaker mapping handling failed. transcriptId={}", transcriptId, e);
            slackMessageService.postErrorMessage(pending.getChannelId(), pending.getThreadTs(),
                    "회의 분석 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
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
        meeting.setTitle(sanitizeMeetingTitle(filename));
        meeting.setCreatedBy(user.getId());
        return meetingRepository.save(meeting);
    }

    private MethodsClient client() {
        return Slack.getInstance().methods(botToken);
    }

    private RecordingResponse downloadAndUpload(Long meetingId, String downloadUrl, long fileSize, String filename) throws Exception {
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

    private SlackMappedUser resolveSlackMappedUser(String slackUserId) throws Exception {
        UsersInfoResponse userInfo = client().usersInfo(r -> r.user(slackUserId));
        if (!userInfo.isOk()) {
            throw new IllegalStateException("Failed to resolve Slack user: " + slackUserId);
        }

        String email = userInfo.getUser().getProfile().getEmail();
        User user = resolveUser(email, slackUserId);

        String displayName = userInfo.getUser().getProfile().getDisplayName();
        if (!StringUtils.hasText(displayName)) {
            displayName = userInfo.getUser().getProfile().getRealName();
        }
        if (!StringUtils.hasText(displayName)) {
            displayName = user.getName();
        }

        if (!StringUtils.hasText(user.getName()) || !displayName.equals(user.getName())) {
            user.setName(displayName);
            userRepository.save(user);
        }

        return new SlackMappedUser(user.getId(), slackUserId, displayName);
    }

    private void upsertSpeakerMapping(MeetingTranscript transcript, String label, SlackMappedUser mappedUser) {
        MeetingTranscript.SpeakerMappingEmbedded mapping = transcript.getSpeakerMappings().stream()
                .filter(item -> label.equals(item.getSpeakerLabel()))
                .findFirst()
                .orElseGet(() -> {
                    MeetingTranscript.SpeakerMappingEmbedded created = new MeetingTranscript.SpeakerMappingEmbedded();
                    created.setSpeakerLabel(label);
                    transcript.getSpeakerMappings().add(created);
                    return created;
                });

        mapping.setUserId(mappedUser.getUserId());
        mapping.setSlackUserId(mappedUser.getSlackUserId());
        mapping.setUserName(mappedUser.getDisplayName());

        transcript.getSegments().stream()
                .filter(segment -> label.equals(segment.getSpeakerLabel()))
                .forEach(segment -> segment.setUserId(mappedUser.getUserId()));
    }

    private MeetingNotionExportResponse tryExportToNotion(PendingAnalysis pending) {
        try {
            return meetingExportService.exportToNotion(pending.getMeetingId(), pending.getUserId(), true);
        } catch (Exception e) {
            log.info("Notion export skipped. meetingId={}, reason={}", pending.getMeetingId(), e.getMessage());
            return null;
        }
    }

    private Comparator<Task> taskComparator() {
        return Comparator
                .comparing((Task task) -> task.getStatus() == null ? TaskStatus.TODO : task.getStatus())
                .thenComparing(task -> task.getDueDate() == null ? LocalDateTime.MAX : task.getDueDate())
                .thenComparing(task -> StringUtils.hasText(task.getAssigneeName()) ? task.getAssigneeName() : "zzz")
                .thenComparing(task -> StringUtils.hasText(task.getTitle()) ? task.getTitle() : "");
    }

    private String sanitizeMeetingTitle(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "Slack 공유 회의";
        }
        return filename.replaceFirst("\\.[^.]+$", "");
    }

    private String sanitizeFilename(String name) {
        if (!StringUtils.hasText(name)) {
            return "recording.m4a";
        }
        String lower = name.toLowerCase();
        if (lower.endsWith(".m4a") || lower.endsWith(".mp3") || lower.endsWith(".mp4")) {
            return name;
        }
        return name + ".m4a";
    }
}
