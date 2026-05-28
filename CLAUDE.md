# MeetingApp Backend — Project Context

## 프로젝트 개요
- **목적**: 회의 관리 백엔드 (STT, AI 분석, Slack 연동)
- **주 인터페이스**: 웹 앱 — 회의 녹음·STT·AI 분석·캘린더 관리
- **Slack**: 보조 인터페이스 — 기존 Slack 사용자가 앱 기능을 Slack 안에서도 이용할 수 있도록 지원
- **팀 프로젝트**: 한성대 캡스톤 2026

## 기술 스택
| 분류 | 기술 |
|------|------|
| Framework | Java 17, Spring Boot |
| DB (관계형) | Azure MySQL — 팀원 운영, 기존 구조 유지 |
| DB (문서형) | MongoDB Atlas (Azure, Korea Central) |
| ORM | Spring Data JPA (MySQL), Spring Data MongoDB |
| Storage | AWS S3 (`hansung-capstone-2026`, ap-northeast-2) |
| STT | Assembly AI (화자 분리 포함) |
| AI 분석 | Google Gemini 2.5 Flash |
| Auth | JWT + OAuth2 (Google, Notion) |
| 외부 연동 | Notion 캘린더, Slack |

## 패키지 구조
```
com.capston.demo
├── DemoApplication.java
├── TestController.java
├── dev
│   └── DevCalendarTestDataController.java        # 테스트용 더미 데이터
├── domain
│   ├── ai
│   │   ├── controller      MeetingAnalysisController
│   │   ├── controllerDocs  MeetingAnalysisControllerDocs
│   │   ├── dto             AssemblyAiTranscriptResult, GeminiAnalysisResult, GeminiCorrectionResult (internal)
│   │   │                   GeminiAnalyzeResponse, MeetingAnalyzeResponse, TranscribeResponse (response)
│   │   └── service         AssemblyAiService, GeminiAiService, MeetingAnalysisService
│   ├── calender
│   │   ├── controller      CalendarController, EventController, TaskController
│   │   ├── controllerDocs  CalendarControllerDocs, EventControllerDocs, TaskControllerDocs
│   │   ├── dto/request     EventCreateRequest, EventUpdateRequest
│   │   │                   TaskCreateRequest, TaskUpdateRequest
│   │   │                   NotionSyncRequestDto
│   │   ├── dto/response    EventResponse, TaskResponse, TaskStatsResponse
│   │   ├── entity          Event, EventParticipant(Id), Task, TaskSource, TaskStatus, ParticipantStatus
│   │   ├── repository      EventRepository, TaskRepository
│   │   └── service         EventService, TaskService, NotionCalendarService
│   ├── meeting
│   │   ├── controller      MeetingController
│   │   ├── controllerDocs  MeetingControllerDocs
│   │   ├── dto/request     MeetingRequest, SpeakerMappingRequest, TranscriptRequest
│   │   ├── dto/response    MeetingResponse, MeetingSummaryResponse, TranscriptResponse,
│   │   │                   SpeakerMappingResponse, MeetingNotionExportResponse
│   │   ├── dto/export      MeetingExportReportModel
│   │   ├── entity          Meeting, MeetingRecording, MeetingTranscript(MongoDB), RecordingStatus
│   │   ├── repository      MeetingRepository, MeetingRecordingRepository,
│   │   │                   MeetingTranscriptMongoRepository
│   │   └── service         MeetingService, MeetingTranscriptService,
│   │                       MeetingExportService, NotionMeetingNotesService
│   ├── recording
│   │   ├── controller      RecordingController
│   │   ├── controllerDocs  RecordingControllerDocs
│   │   ├── dto             PresignedUploadRequest, RecordingResponse, PresignedUrlResponse
│   │   └── service         RecordingService
│   ├── slack
│   │   ├── controller      SlackEventController, SlackInteractionController
│   │   └── service         SlackService
│   └── user
│       ├── controller      AuthController, OAuth2Controller, UserController, WorkspaceController
│       ├── controllerDocs  WorkspaceControllerDocs
│       ├── entity          User, RefreshToken, UserNotionAccount
│       │                   Workspace, WorkspaceMember, WorkspaceMemberId
│       ├── repository      UserRepository, RefreshTokenRepository, UserNotionAccountRepository
│       │                   WorkspaceRepository, WorkspaceMemberRepository
│       ├── dto/workspace   WorkspaceCreateRequest, WorkspaceUpdateRequest, WorkspaceInviteRequest,
│       │                   WorkspaceResponse, WorkspaceMemberResponse
│       └── service         AuthService, UserService, GoogleOAuth2Service, NotionOAuth2Service,
│                           OAuthUserService, MyUserDetailsService, S3Service, WorkspaceService
└── global
    ├── config      SecurityConfig, JwtAuthenticationFilter, MongoConfig, S3Config,
    │               OAuth2Config, SwaggerConfig
    ├── exception   GlobalExceptionHandler, BusinessException, ErrorCode
    │               DuplicateEmailException, ExpiredTokenException,
    │               InvalidTokenException, OAuthAuthenticationException
    ├── security    CustomUserDetails
    └── util        JwtUtil, S3Util
```

## DB 분리 전략 (완료)

### MySQL 유지 (관계형 데이터)
- `users`, `workspaces`, `workspace_members`
- `meetings`, `meeting_recordings`
- `events`, `event_participants`, `tasks`
- `refresh_tokens`, `user_notion_accounts`

### MongoDB 사용 (비정형 대용량 데이터)
- `meeting_transcripts` → `MeetingTranscript` Document
- `transcript_segments` → `MeetingTranscript.segments[]` 배열로 내장
- `speaker_mappings` → `MeetingTranscript.speakerMappings[]` 배열로 내장

### 연결 키
- MongoDB Document는 `meetingId` 필드로 MySQL `meetings.id` (Long) 참조
- 양방향 FK 없음, 애플리케이션 레벨에서 조인

## 주요 엔티티 요약

### Meeting (MySQL)
- `id`, `title`, `workspaceId` (Long, nullable — Slack 생성 시 null), `createdBy` (userId), `createdAt`
- `recordings[]` — OneToMany → MeetingRecording
- 접근제어: workspaceId 있으면 멤버십, 없으면 createdBy 이중 처리

### Workspace (MySQL) — user 패키지에 위치
- `id`, `name`, `slug` (unique), `owner` (User FK), `createdAt`
- `meetingCategory` (nullable) — 회의 도메인 분야 (예: 개발, 디자인, 마케팅). STT 문맥보정 시 Gemini 프롬프트에 전달
- `meetingContext` (nullable) — 추가 문맥 설명 (예: "React, Spring Boot 사용 중인 협업 도구 프로젝트")
- WorkspaceMember: `workspaceId + userId` 복합키, `role` (owner/admin/member), `joinedAt`

### MeetingTranscript (MongoDB Document)
- `_id` (String), `meetingId` (Long, MySQL 참조), `recordingId` (Long)
- `originalFullText` (AssemblyAI 원문), `correctedFullText` (Gemini 교정본), `displayFullText` (교정 표시본)
- `fullText` (= correctedFullText, 분석용), `summary`, `keywords[]`, `analyzedAt`, `createdAt`
- `segments[]` — `SegmentEmbedded` (speakerLabel, originalContent, correctedContent, displayContent, corrections[], content, startSec, endSec, sequence)
  - `corrections[]` — `CorrectionEmbedded` (original, corrected, reason)
- `speakerMappings[]` — `SpeakerMappingEmbedded` (speakerLabel, userId, userName, slackUserId)

### Task (MySQL)
- `id`, `title`, `description`, `assigneeId`, `assigneeName`, `createdBy`
- `meetingId`, `workspaceId`, `dueDate`
- `status` (TaskStatus: TODO/IN_PROGRESS/DONE), `source` (TaskSource: AI_GENERATED/MANUAL)
- **중요**: Gemini 생성 시 `createdBy` = meeting.createdBy, `workspaceId` = meeting.workspaceId 자동 설정

### Event (MySQL)
- `id`, `title`, `description`, `location`, `startAt`, `endAt`, `isAllDay`
- `workspaceId`, `createdBy`, `createdByName`, `meetingId`, `color`, `createdAt`
- `participants[]` — EventParticipant (userId, status: PENDING/ACCEPTED/DECLINED)
- **중요**: Gemini AI 분석에서 Event 자동 추출 제거됨 — 수동 생성만 지원. Gemini는 Task만 추출

## 구현된 API 엔드포인트

### 인증 (`/api/auth`)
- `POST /api/auth/login`, `POST /api/auth/logout`, `POST /api/auth/refresh`

### Google OAuth (`/api/oauth2/google`)
- `GET /api/oauth2/google/auth-url` — 인증 URL 반환
- `GET /api/oauth2/google/callback` — 웹 리다이렉트 콜백
- `POST /api/oauth2/google/callback` — 앱용 code 교환 → JWT 발급

### Notion OAuth (`/api/oauth2/notion`)
- `GET /api/oauth2/notion/auth-url` — 로그인용 인증 URL
- `GET /api/oauth2/notion/callback`, `POST /api/oauth2/notion/callback` — 로그인 콜백
- `GET /api/oauth2/notion/link/auth-url` — 기존 계정에 Notion 연동용 URL
- `GET /api/oauth2/notion/link/callback` — 302 딥링크 리다이렉트 (`meetflow://notion/link?code=...`)
- `POST /api/oauth2/notion/link` — 앱에서 code 받아 연동 완료 (JWT 필요)
- `GET /api/oauth2/notion/status` — 연동·캘린더 설정 상태 조회 `{ linked, calendarConfigured, notionName, calendarName }`
- `GET /api/oauth2/notion/calendar-targets` — 연동된 Notion DB 목록 조회 (캘린더 선택용)
- `POST /api/oauth2/notion/calendar-targets` — Notion에 캘린더 DB 신규 생성 후 자동 등록
- `PUT /api/oauth2/notion/calendar-database` — 기존 DB를 캘린더로 등록 (databaseId 또는 databaseUrl)
- `PUT /api/oauth2/notion/meeting-notes-database` — 회의록 내보내기용 DB 등록

### 사용자 (`/api/user`)
- `POST /api/user/register`, `GET /api/user/profile`
- `PATCH /api/user/profile/name`, `PATCH /api/user/profile/image`, `PATCH /api/user/password`
- `DELETE /api/user/account`, `GET /api/user/presigned-url`

### 워크스페이스 (`/api/workspaces`)
- `POST /api/workspaces` — 생성 (생성자 자동으로 owner 멤버 등록, `meetingCategory`/`meetingContext` 선택 포함)
- `GET /api/workspaces` — 내가 속한 워크스페이스 목록 (owner + member 모두)
- `PATCH /api/workspaces/{id}` — 이름·카테고리·문맥 수정 (owner만 가능, 부분 업데이트)
- `DELETE /api/workspaces/{id}` — 삭제 (owner만 가능, 멤버십도 함께 삭제)
- `POST /api/workspaces/{id}/members` — 이메일로 멤버 초대
- `GET /api/workspaces/{id}/members` — 멤버 목록 (화자 매핑 드롭다운용)

### 회의 (`/api/meetings`)
- `POST /api/meetings` — 회의 생성 (workspaceId 필수, 멤버십 검증)
- `GET /api/meetings?workspaceId=` — 워크스페이스 소속 회의 목록
- `GET /api/meetings` — 내가 생성한 회의 목록 (Slack용)
- `GET /api/meetings/{id}` — 회의 단건 조회
- `GET /api/meetings/{id}/summary` — 회의 대시보드 (요약·키워드·Task 상태 분포·Event 개수)
- `DELETE /api/meetings/{id}` — 회의 삭제
- `POST /api/meetings/{meetingId}/transcript` — 트랜스크립트 저장
- `GET /api/meetings/{meetingId}/transcript` — 트랜스크립트 조회 (segments에 speakerName 포함)
- `PUT /api/meetings/transcripts/{transcriptId}/speaker-mappings` — 화자 매핑 저장
- `GET /api/meetings/transcripts/{transcriptId}/speaker-mappings` — 화자 매핑 조회

### AI 분석 (`/api/meetings`)
- `POST /api/meetings/{meetingId}/recordings/{recordingId}/transcribe` — STT 전사 + 문맥보정
  - AssemblyAI STT → Gemini 문맥보정 (워크스페이스 카테고리/문맥 기반) → MongoDB 저장
  - 응답: `originalFullText`, `correctedFullText`, `displayFullText`, `segments[]` (corrections 포함)
  - 문맥보정 실패 시 원문 그대로 폴백 처리
- `POST /api/meetings/transcripts/{transcriptId}/gemini-analyze` — Gemini AI 분석
  - Task만 추출 (Event 자동 추출 없음)
  - 재실행 시 기존 AI 생성 Task 자동 삭제 후 재저장 (중복 방지)
  - speakerLabel → userId 역조회로 task.assigneeId 자동 설정
- `POST /api/meetings/{meetingId}/recordings/{recordingId}/analyze` — STT + 문맥보정 + Gemini 분석 통합 1단계 API

### 녹음 (`/api/recordings`)
- `POST /api/recordings/upload?meetingId=` — 서버 경유 업로드
- `POST /api/recordings/presigned-upload-url` — S3 직접 업로드용 Presigned URL
- `GET /api/recordings?meetingId=` — 녹음 목록
- `GET /api/recordings/{id}/presigned-url` — 재생용 Presigned URL
- `PATCH /api/recordings/{id}/status` — 상태 변경
- `DELETE /api/recordings/{id}` — 삭제

### 할일 (`/api/tasks`)
- `GET /api/tasks?meetingId=&workspaceId=&assigneeId=&status=&dueBefore=` — 목록 조회
  - 팀 전체 조회 (createdBy 필터 없음)
  - `status`: TODO / IN_PROGRESS / DONE 필터
  - `dueBefore`: 마감일 이전 필터 (yyyy-MM-dd'T'HH:mm:ss)
- `GET /api/tasks/stats?workspaceId=&meetingId=` — 상태 분포 (칸반 보드용)
- `GET /api/tasks/{id}` — 단건 조회
- `POST /api/tasks` — 수동 생성
- `PATCH /api/tasks/{id}` — 수정 (부분 업데이트 — 보낸 필드만 변경)
- `DELETE /api/tasks/{id}` — 삭제

### 일정 (`/api/events`)
- `GET /api/events?meetingId=&workspaceId=` — 목록 조회
  - 팀 전체 조회 (createdBy 필터 없음)
  - 응답에 `relatedTasks[]` 포함 (같은 meetingId Task의 id/title/status/assigneeName/dueDate)
- `GET /api/events/{id}` — 단건 조회 (relatedTasks 포함)
- `POST /api/events` — 수동 생성
- `PATCH /api/events/{id}` — 수정 (부분 업데이트)
- `DELETE /api/events/{id}` — 삭제

### 캘린더/Notion 동기화 (`/api/calendar`)
- `GET /api/calendar/workspaces/{workspaceId}/events` — 워크스페이스 이벤트 조회
- `POST /api/calendar/events/{eventId}/notion-sync` — 단일 이벤트 Notion 동기화
- `POST /api/calendar/workspaces/{workspaceId}/notion-sync` — 워크스페이스 전체 Notion 동기화
- `POST /api/calendar/events/notion-sync-batch` — 이벤트 ID 목록 일괄 동기화

### 회의록 내보내기 (`/api/meetings`)
- `GET /api/meetings/{meetingId}/export/pdf?includeEvents=` — 회의 리포트 PDF 다운로드
  - Thymeleaf HTML 템플릿 → openhtmltopdf → PDF 변환 (맑은 고딕 한글 폰트)
  - 내용: 요약, 키워드, 할일 목록, (includeEvents=true 시) 일정 목록
  - Gemini 분석 완료(summary 존재) 후에만 가능
- `POST /api/meetings/{meetingId}/export/notion?includeEvents=` — Notion 회의록 DB로 내보내기
  - 이미 내보낸 회의면 동일 페이지 **갱신** (notionPageId 기억)
  - 전제: Notion OAuth 연동 + `PUT /api/oauth2/notion/meeting-notes-database` 등록 필요

### Slack (`/slack`)
- `POST /slack/events` — Slack 이벤트 수신 (file_shared 등)
- `POST /slack/interactions` — 버튼 클릭, Modal 제출

## 보안 원칙
- 회의/녹음: `workspaceId` 있으면 `WorkspaceMember` 멤버십 검증, 없으면(Slack 생성) `createdBy` 검증
- Task/Event 수정·삭제: `workspaceId` 있으면 멤버십 검증, 없으면 `createdBy` 검증
- 워크스페이스 삭제: owner만 가능
- 워크스페이스 멤버 초대/조회: 요청자가 해당 워크스페이스 멤버인지 검증
- JWT 인증 기반, Spring Security 사용

## 개발 원칙
- 코드는 Java/Spring Boot 기준
- 기존 MySQL 구조는 최대한 유지, MongoDB는 비정형 데이터만 추가
- 실무 관점 조언 선호
- 불필요한 추상화, 헬퍼 클래스 추가 금지
- 보안 취약점(SQL Injection, 인가 누락 등) 주의
- Swagger 문서는 controllerDocs 인터페이스로 분리 (Controller implements XxxControllerDocs)

## 환경 변수 (application.yml 참고)
- `DATASOURCE_URL/USERNAME/PASSWORD` — Azure MySQL
- `AWS_ACCESS_KEY_ID/SECRET_ACCESS_KEY` — S3
- `ASSEMBLYAI_API_KEY` — STT
- `GEMINI_API_KEY` — AI 분석
- `JWT_SECRET` — 토큰 서명
- `GOOGLE_CLIENT_ID/SECRET`, `NOTION_CLIENT_ID/SECRET` — OAuth2
- `SLACK_BOT_TOKEN` — Slack Bot Token (application.yml: `slack.bot-token`)

---

## 앱 서비스 시나리오 (전체 방향성)

> 핵심 가치: 웹 앱이 주 서비스. Slack은 보조 인터페이스로, 기존 Slack 사용자가 별도 앱 전환 없이 회의 관리 기능(STT·AI 분석·할일 등록 등)을 Slack 내에서도 이용할 수 있도록 지원

```
1. 로그인 후 워크스페이스 생성, 다른 사용자 초대
   - meetingCategory (예: 개발, 디자인), meetingContext (기술스택 등) 설정 시 STT 문맥보정 품질 향상
   - 이후 PATCH /api/workspaces/{id} 로 언제든 수정 가능 (owner만)

2. 워크스페이스에서 녹음 파일 업로드
   → S3에 업로드 (Presigned URL 직접 또는 서버 경유)

3. STT 전사 + 문맥보정
   POST /api/meetings/{meetingId}/recordings/{recordingId}/transcribe
   → AssemblyAI STT (화자 자동 분리: SPEAKER_00, SPEAKER_01, ...)
   → Gemini 문맥보정 (워크스페이스 카테고리/문맥 기반 도메인 용어 교정)
   → 응답: originalFullText / correctedFullText / displayFullText (교정 표시본)

4. STT 완료 후 대화 내용 확인 + 화자 매핑
   - GET /api/workspaces/{id}/members 로 멤버 목록 조회 → 드롭다운 선택
   - PUT speaker-mappings 시 반드시 userId + userName 함께 전송 (userId null 금지)
   - Slack 사용 시 채널 내 버튼 클릭 → Modal에서 Slack 멤버 선택

5. 화자 매핑 완료 후 Gemini AI 분석 실행
   POST /api/meetings/transcripts/{transcriptId}/gemini-analyze
   - 요약, 키워드, 할일(Task)만 자동 추출 (Event 자동 추출 없음)
   - speakerLabel → userId 역조회로 Task.assigneeId 자동 설정
   - 재실행 시 기존 AI 생성 Task 자동 삭제 후 재저장 (중복 없음)

6. 캘린더(인앱)에서 AI 추출 결과 검토 및 수정
   A. AI 추출 할일을 PATCH로 수정 (담당자, 마감일, 상태 등)
   B. 불필요한 항목 DELETE
   C. 누락된 항목 수동으로 POST 추가 (Task 또는 Event 직접 생성)

7. 워크스페이스 전체 할일 상태 현황 조회 (칸반 보드)
   - GET /api/tasks/stats?workspaceId= 로 TODO/IN_PROGRESS/DONE 분포 확인
   - GET /api/meetings/{id}/summary 로 회의별 대시보드 확인

8. 회의록 내보내기 (선택)
   A. PDF: GET /api/meetings/{id}/export/pdf → 요약·할일·일정 포함 PDF 다운로드
   B. Notion: POST /api/meetings/{id}/export/notion → 연동된 Notion DB에 페이지 생성/갱신
      - 전제: Notion OAuth 연동 (GET /api/oauth2/notion/link/auth-url 플로우)
              + 회의록 DB 등록 (PUT /api/oauth2/notion/meeting-notes-database)

9. 선택적으로 Notion 캘린더 연동
   - Notion OAuth 연동 후 캘린더 DB 선택/생성 (GET·POST /api/oauth2/notion/calendar-targets)
   - 인앱 Event를 Notion 캘린더로 동기화 (POST /api/calendar/.../notion-sync)
```

### 캘린더 원칙
- 기본: 인앱 캘린더 (Tasks, Events)
- Gemini AI 분석: Task만 자동 추출. Event는 수동 생성만 지원
- 선택: Notion 연동 (원하는 경우에만 내보내기)
- 팀 전체 조회: meetingId/workspaceId 기준 조회 시 createdBy 필터 없이 팀 전체 반환

---

## Slack 연동 현황 및 향후 계획

### 현재 구현된 Slack 플로우
```
채널에 오디오 파일 업로드 (m4a, mp3)
  → file_shared 이벤트 수신 (/slack/events)
  → Slack 유저 이메일로 DB User 조회/자동생성
  → Meeting 자동 생성 (createdBy = user.id, workspaceId = null)
  → S3 업로드
  → AssemblyAI STT (화자분리 포함) + Gemini 문맥보정
  → Gemini AI 분석 (요약, 키워드, 할일 추출 — Event 없음)
  → 결과를 업로더에게 DM으로 전송  ← 현재 여기까지
```

### 목표 플로우 (미구현)
```
... STT 완료
  → 채널에 "화자 매핑하기" 버튼 메시지 게시
  → 버튼 클릭 → Slack Modal 열림 (SPEAKER_00/01 → Slack 워크스페이스 멤버 선택)
  → Modal 제출 → speakerMappings MongoDB 저장 (userId + slackUserId + userName)
  → Gemini AI 분석 (매핑된 이름/userId 반영, assigneeId 자동 설정)
  → 결과를 채널에 게시 (DM 아님)
  → Slack Lists 생성 (할일 항목, 담당자, 마감일 포함)
```

### 미구현 작업 목록

#### 1. SecurityConfig — `/slack/interactions` 허용 확인
```java
.requestMatchers("/slack/events", "/slack/interactions").permitAll()
```

#### 2. SlackEventController — channel_id 추출
- `file_shared` 이벤트 payload에서 `channel_id` 파싱
- `slackService.handleFileShared(fileId, userId, channelId)` 로 시그니처 변경

#### 3. SlackService 리팩터링
- STT 완료 후 Gemini 즉시 호출 대신:
  - 화자 레이블 추출 → `PendingAnalysis` 인메모리 저장 (ConcurrentHashMap)
  - 채널에 "화자 매핑하기" 버튼 메시지 게시
- `openSpeakerMappingModal(triggerId, pendingKey)` — `views.open` API로 Modal 생성
- `handleSpeakerMappingSubmit(pendingKey, values)` (@Async) — 매핑 저장 → Gemini → 채널 게시
- `sendDm` → `postToChannel` 변경

#### 4. SlackInteractionController 구현
- `POST /slack/interactions` (application/x-www-form-urlencoded)
- `block_actions` → 버튼 클릭 → Modal 열기
- `view_submission` → Modal 제출 → 화자 매핑 저장 + Gemini 분석

#### 5. Slack Lists 생성 (유료 플랜 필요)
- `lists.create` API, `lists:write` 스코프 필요

### Meeting 엔티티 현황
- `workspaceId`: 앱에서 생성 시 필수, Slack 생성 시 null
- `channelId`, `startedAt`, `endedAt`: DB 컬럼만 유지, 코드에서 미사용
- 접근제어: workspaceId 있으면 멤버십, 없으면 createdBy 이중 처리

### Slack vs 앱 API 분리 여부
- **분리 불필요** — URL 네임스페이스만 구분
  - `/api/...` — 앱 UI용 (JWT 인증 필수)
  - `/slack/...` — Slack 이벤트/인터랙션 수신 (permitAll)
- Slack 내부 로직은 기존 service 레이어 그대로 재사용
