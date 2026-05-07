# MeetingApp Backend — Project Context

## 프로젝트 개요
- **목적**: 회의 관리 백엔드 (STT, AI 분석, Slack 연동)
- **주 인터페이스**: Slack — 채널에 녹음 파일 업로드 → 자동 분석 → 결과 채널 게시
- **팀 프로젝트**: 한성대 캡스톤 2026

## 기술 스택
| 분류 | 기술 |
|------|------|
| Framework | Java 17, Spring Boot |
| DB (관계형) | Azure MySQL — 팀원 운영, 기존 구조 유지 |
| DB (문서형) | MongoDB Atlas (Azure, Korea Central) |
| ORM | Spring Data JPA (MySQL), Spring Data MongoDB |
| Storage | AWS S3 (`hansung-capstone-2026`, ap-northeast-2) |
| STT | Assembly AI |
| AI 분석 | Google Gemini 2.5 Flash |
| Auth | JWT + OAuth2 (Google, Notion) |
| 외부 연동 | Notion 캘린더 |

## 패키지 구조
```
com.capston.demo
├── DemoApplication.java
├── TestController.java
├── dev
│   └── DevCalendarTestDataController.java        # 테스트용 더미 데이터
├── domain
│   ├── ai
│   │   ├── controller  MeetingAnalysisController
│   │   ├── dto         AssemblyAiTranscriptResult, GeminiAnalysisResult (internal)
│   │   │               GeminiAnalyzeResponse, TranscribeResponse (response)
│   │   └── service     AssemblyAiService, GeminiAiService, MeetingAnalysisService
│   ├── calender
│   │   ├── controller  CalendarController, EventController, TaskController
│   │   ├── entity      Event, EventParticipant(Id), Task, TaskSource, TaskStatus, ParticipantStatus
│   │   ├── repository  EventRepository, TaskRepository
│   │   └── service     EventService, TaskService, NotionCalendarService
│   ├── meeting
│   │   ├── controller  MeetingController
│   │   ├── entity      Meeting, MeetingRecording, MeetingTranscript(MongoDB), RecordingStatus
│   │   ├── repository  MeetingRepository, MeetingRecordingRepository, MeetingTranscriptMongoRepository
│   │   └── service     MeetingService, MeetingTranscriptService
│   ├── recording
│   │   ├── controller  RecordingController
│   │   ├── dto         PresignedUploadRequest, RecordingResponse, PresignedUrlResponse
│   │   └── service     RecordingService
│   ├── slack
│   │   ├── controller  SlackEventController, SlackInteractionController
│   │   └── service     SlackService
│   └── user
│       ├── controller  AuthController, OAuth2Controller, UserController, WorkspaceController
│       ├── entity      User, RefreshToken, UserNotionAccount
│       │               Workspace, WorkspaceMember, WorkspaceMemberId  ← user 패키지에 혼재 (추후 분리 고려)
│       ├── repository  UserRepository, RefreshTokenRepository, UserNotionAccountRepository
│       │               WorkspaceRepository, WorkspaceMemberRepository
│       ├── dto         workspace/ (WorkspaceCreateRequest, WorkspaceInviteRequest,
│       │                          WorkspaceResponse, WorkspaceMemberResponse)
│       └── service     AuthService, UserService, GoogleOAuth2Service, NotionOAuth2Service,
│                       OAuthUserService, MyUserDetailsService, S3Service, WorkspaceService
└── global
    ├── config      SecurityConfig, JwtAuthenticationFilter, MongoConfig, S3Config,
    │               OAuth2Config, SwaggerConfig
    ├── exception   GlobalExceptionHandler, DuplicateEmailException,
    │               ExpiredTokenException, InvalidTokenException, OAuthAuthenticationException
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
- `meeting_transcripts` → `MeetingTranscript` Document (완료)
- `transcript_segments` → `MeetingTranscript.segments[]` 배열로 내장 (완료)
- `speaker_mappings` → `MeetingTranscript.speakerMappings[]` 배열로 내장 (완료)
- Gemini AI 분석 결과 raw JSON (향후)

### 연결 키
- MongoDB Document는 `meetingId` 필드로 MySQL `meetings.id` (Long) 참조
- 양방향 FK 없음, 애플리케이션 레벨에서 조인

## 주요 엔티티 요약

### Meeting (MySQL)
- `id`, `title`, `workspaceId` (Long, nullable — Slack 생성 시 null), `createdBy` (userId), `createdAt`
- `recordings[]` — OneToMany → MeetingRecording

### Workspace (MySQL) — user 패키지에 위치
- `id`, `name`, `slug` (unique), `owner` (User FK), `createdAt`
- WorkspaceMember: `workspaceId + userId` 복합키, `role` (owner/admin/member), `joinedAt`

### MeetingTranscript (MongoDB Document)
- `_id` (String), `meetingId` (Long, MySQL 참조), `recordingId` (Long)
- `fullText`, `summary`, `analyzedAt`, `createdAt`
- `segments[]` — `SegmentEmbedded` (speakerLabel, userId, content, startSec, endSec, sequence)
- `speakerMappings[]` — `SpeakerMappingEmbedded` (speakerLabel, userId, userName, slackUserId)

### Task (MySQL)
- `id`, `title`, `description`, `assigneeId`, `meetingId`, `workspaceId`, `dueDate`
- `status` (TaskStatus enum), `source` (TaskSource enum — AI/MANUAL)

### Event (MySQL)
- `id`, `title`, `description`, `startAt`, `endAt`, `workspaceId`, `createdBy`
- `participants[]` — EventParticipant (userId, status)

## 구현된 API 엔드포인트

### 인증 (`/api/auth`, `/api/oauth2`)
- `POST /api/auth/login`, `POST /api/auth/logout`, `POST /api/auth/refresh`
- `POST /api/oauth2/google`, `POST /api/oauth2/notion`

### 사용자 (`/api/user`)
- `POST /api/user/register`, `GET /api/user/profile`
- `PATCH /api/user/profile/name`, `PATCH /api/user/profile/image`, `PATCH /api/user/password`
- `DELETE /api/user/account`, `GET /api/user/presigned-url`

### 워크스페이스 (`/api/workspaces`)
- `POST /api/workspaces` — 생성 (생성자 자동으로 owner 멤버 등록)
- `GET /api/workspaces` — 내가 속한 워크스페이스 목록 (owner + member 모두)
- `POST /api/workspaces/{id}/members` — 이메일로 멤버 초대
- `GET /api/workspaces/{id}/members` — 멤버 목록 (화자 매핑 드롭다운용)

### 회의 (`/api/meetings`)
- `POST /api/meetings` — 회의 생성 (workspaceId 필수, 멤버십 검증)
- `GET /api/meetings?workspaceId=` — 워크스페이스 소속 회의 목록
- `GET /api/meetings` — 내가 생성한 회의 목록 (Slack용)
- `GET /api/meetings/{id}` — 회의 단건 조회
- `DELETE /api/meetings/{id}` — 회의 삭제
- `POST /api/meetings/{meetingId}/transcript` — 트랜스크립트 저장
- `GET /api/meetings/{meetingId}/transcript` — 트랜스크립트 조회
- `PUT /api/meetings/transcripts/{transcriptId}/speaker-mappings` — 화자 매핑 저장
- `GET /api/meetings/transcripts/{transcriptId}/speaker-mappings` — 화자 매핑 조회

### AI 분석 (`/api/meetings`)
- `POST /api/meetings/{meetingId}/recordings/{recordingId}/transcribe` — STT 전사
- `POST /api/meetings/transcripts/{transcriptId}/gemini-analyze` — Gemini AI 분석

### 녹음 (`/api/recordings`)
- `POST /api/recordings/upload?meetingId=` — 서버 경유 업로드
- `POST /api/recordings/presigned-upload-url` — S3 직접 업로드용 Presigned URL
- `GET /api/recordings?meetingId=` — 녹음 목록
- `GET /api/recordings/{id}/presigned-url` — 재생용 Presigned URL
- `PATCH /api/recordings/{id}/status` — 상태 변경
- `DELETE /api/recordings/{id}` — 삭제

### 캘린더 (`/api/tasks`, `/api/events`, `/api/calendar`)
- `GET /api/tasks?meetingId=&workspaceId=&assigneeId=` — 할일 목록 조회
- Event CRUD, Notion 동기화

### Slack (`/slack`)
- `POST /slack/events` — Slack 이벤트 수신 (file_shared 등)
- `POST /slack/interactions` — 버튼 클릭, Modal 제출

## 보안 원칙
- 회의/녹음 접근: `workspaceId` 있으면 `WorkspaceMember` 멤버십 검증, 없으면(Slack 생성) `createdBy` 검증
- 워크스페이스 멤버 초대/조회: 요청자가 해당 워크스페이스 멤버인지 검증
- JWT 인증 기반, Spring Security 사용

## 개발 원칙
- 코드는 Java/Spring Boot 기준
- 기존 MySQL 구조는 최대한 유지, MongoDB는 비정형 데이터만 추가
- 실무 관점 조언 선호
- 불필요한 추상화, 헬퍼 클래스 추가 금지
- 보안 취약점(SQL Injection, 인가 누락 등) 주의

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

> 핵심 가치: Slack 메신저를 사용하면서 동시에 회의 관리 앱 기능을 활용 가능 (두 인터페이스 공존)

```
1. 로그인 후 워크스페이스 생성, 다른 사용자 초대 가능

2. 워크스페이스에서 녹음 파일 업로드
   → STT 자동 변환 시작 (화자 자동 분리: 화자A, 화자B, ...)

3. STT 완료 후 대화 내용 확인 + 화자 매핑
   - 수동 이름 입력 또는 워크스페이스 멤버 중 선택
   - Slack 사용 시 채널 내 버튼 클릭 → Modal에서 매핑 가능

4. 화자 매핑 완료 후 AI 분석 결과 표시
   - 화자 이름이 적용된 전체 대화록
   - 회의 요약

5. 캘린더(인앱)에 할일 등록 — 두 가지 방법
   A. AI가 추출한 할일 리스트 검토 후 등록 (담당자, 마감일 확인/수정 가능)
   B. 사용자가 직접 수동 등록 (담당자, 업무 내용, 마감일 직접 입력)

6. 워크스페이스 내 녹음 파일 누적 업로드 가능
   → 회의마다 캘린더 할일 지속 갱신

7. 선택적으로 Notion 캘린더 연동 가능
   (인앱 캘린더 데이터를 Notion으로 내보내기)
```

### 캘린더 원칙
- 기본: 인앱 캘린더 (Tasks, Events)
- 선택: Notion 연동 (원하는 경우에만 내보내기)
- 할일은 AI 추출 또는 수동 등록 모두 지원, 담당자/마감일 포함

---

## Slack 연동 현황 및 향후 계획

### 현재 구현된 Slack 플로우
```
채널에 오디오 파일 업로드 (m4a, mp3)
  → file_shared 이벤트 수신 (/slack/events)
  → Slack 유저 이메일로 DB User 조회/자동생성
  → Meeting 자동 생성 (createdBy = user.id)
  → S3 업로드
  → AssemblyAI STT (화자분리 포함)
  → Gemini AI 분석 (요약, 키워드, 할일, 이벤트 추출)
  → 결과를 업로더에게 DM으로 전송  ← 현재 여기까지
```

### 목표 플로우 (미구현)
```
... STT 완료
  → 채널에 "화자 매핑" 버튼 메시지 게시
  → 버튼 클릭 → Slack Modal 열림 (화자A/B/C → Slack 워크스페이스 멤버 선택)
  → Modal 제출 → 화자 매핑 MongoDB 저장
  → Gemini AI 분석 (매핑된 이름/slackUserId 반영)
  → 결과를 채널에 게시 (DM 아님)
  → Slack Lists 생성 (할 일 항목, 담당자, 마감일 포함)
```

### 미구현 작업 목록

#### 1. SecurityConfig — `/slack/interactions` 허용
```java
.requestMatchers("/slack/events", "/slack/interactions").permitAll()
```

#### 2. SlackEventController — channel_id 추출
- `file_shared` 이벤트 payload에서 `channel_id` 파싱
- `slackService.handleFileShared(fileId, userId, channelId)` 로 변경

#### 3. SlackService 리팩터링
- `handleFileShared(fileId, userId, channelId)` 로 시그니처 변경
- STT 완료 후 Gemini 즉시 호출 대신:
  - 화자 레이블 추출 → `PendingAnalysis` 인메모리 저장 (ConcurrentHashMap)
  - 채널에 "화자 매핑하기" 버튼 메시지 게시
- `openSpeakerMappingModal(triggerId, pendingKey)` 메서드 추가
  - `views.open` API로 화자별 Slack 멤버 선택 드롭다운 Modal 생성
- `handleSpeakerMappingSubmit(pendingKey, values)` 메서드 추가 (@Async)
  - 매핑 저장 → Gemini 분석 → 채널에 결과 게시 → Slack Lists 생성
- `sendDm` → `postToChannel` 로 변경

#### 4. SlackInteractionController 신규 생성
- `POST /slack/interactions` (application/x-www-form-urlencoded)
- `block_actions` → 버튼 클릭 → `openSpeakerMappingModal` 호출
- `view_submission` → Modal 제출 → `handleSpeakerMappingSubmit` 호출

#### 5. Slack Lists 생성
- `lists.create` API로 채널에 Lists 탭 생성
- 할 일 항목 추가 (담당자=Slack User ID, 마감일 포함)
- **전제조건**: Slack 앱에 `lists:write` 스코프 추가 필요
- **전제조건**: 유료 Slack 플랜 필요

### 화자 매핑 설계 변경 사항
- 기존: speakerLabel → userName (문자열 직접 입력)
- 변경: speakerLabel → Slack User ID + userName 동시 저장
- `SpeakerMappingEmbedded`에 `slackUserId` 필드 추가 필요
- 결과 게시 시 `<@U12345>` 멘션으로 담당자 알림

### Meeting 엔티티 현황
- `workspaceId`: 복원됨 (웹 앱에서 생성 시 필수, Slack 생성 시 null)
- `channelId`, `startedAt`, `endedAt`: DB 컬럼만 유지, 코드에서 제거됨
- 제거된 API: `PATCH /api/meetings/{id}/end` (endMeeting)
- 접근제어: workspaceId 있으면 멤버십, 없으면 createdBy로 이중 처리
