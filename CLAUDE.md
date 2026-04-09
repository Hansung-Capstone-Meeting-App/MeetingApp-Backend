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
├── domain
│   ├── ai          # AssemblyAI STT, Gemini 분석
│   ├── calender    # Event, Task, Notion 연동
│   ├── meeting     # Meeting, MeetingRecording, Transcript, Segment
│   ├── recording   # S3 업로드, presigned URL
│   └── user        # User, OAuth, JWT, Workspace
└── dev             # 테스트용 컨트롤러 (DevCalendarTestDataController)
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

### MeetingTranscript (MongoDB Document)
- `_id` (String), `meetingId` (Long, MySQL 참조), `recordingId` (Long)
- `fullText`, `summary`, `keywords` (List), `analyzedAt`, `createdAt`
- `segments[]` — `SegmentEmbedded` (speakerLabel, userId, content, startSec, endSec, sequence)
- `speakerMappings[]` — `SpeakerMappingEmbedded` (speakerLabel, userId, userName)

## 보안 원칙
- 모든 데이터 접근은 `createdBy` 또는 `userId` 기준으로 소유자 검증 필수
- 다른 사용자의 녹음/회의 데이터 접근 차단 (이미 적용됨)
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

### Meeting 엔티티 정리 내역 (완료, 옵션 A — 코드만 제거, DB 컬럼 유지)
| 제거된 필드 | 이유 |
|-------------|------|
| `workspaceId` | Slack이 주 인터페이스, 워크스페이스 개념 불필요 |
| `channelId` | 커스텀 메신저 채널 개념 → Slack 대체 |
| `startedAt` | startMeeting API 제거로 사용처 없음 |
| `endedAt` | endMeeting API 제거로 사용처 없음 |

제거된 API: `POST /api/meetings` (startMeeting), `PATCH /api/meetings/{id}/end` (endMeeting)
제거된 쿼리 필터: `getMeetings(workspaceId, channelId)` → `getMeetings(createdBy)` 로 단순화
