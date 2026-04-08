# MeetingApp Backend — Project Context

## 프로젝트 개요
- **목적**: 회의 관리 백엔드 (STT, AI 분석, 캘린더 연동)
- **향후 방향**: Slack 봇/에이전트로 기능 노출 예정 (설계 단계)
- **팀 프로젝트**: 한성대 캡스톤 2026

## 기술 스택
| 분류 | 기술 |
|------|------|
| Framework | Java 17, Spring Boot |
| DB (관계형) | Azure MySQL — 팀원 운영, 기존 구조 유지 |
| DB (문서형) | MongoDB Atlas (Azure, Korea Central) — 도입 예정 |
| ORM | Spring Data JPA (MySQL), Spring Data MongoDB (예정) |
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

## DB 분리 전략 (진행 중)

### MySQL 유지 (관계형 데이터)
- `users`, `workspaces`, `workspace_members`
- `meetings`, `meeting_recordings`
- `events`, `event_participants`, `tasks`
- `speaker_mappings`
- `refresh_tokens`, `user_notion_accounts`

### MongoDB 이전 예정 (비정형 대용량 데이터)
- `meeting_transcripts` → `MeetingTranscript` Document
- `transcript_segments` → `MeetingTranscript.segments[]` 배열로 내장
- Gemini AI 분석 결과 raw JSON (향후)

### 연결 키
- MongoDB Document는 `meetingId` 필드로 MySQL `meetings.id` (Long) 참조
- 양방향 FK 없음, 애플리케이션 레벨에서 조인

## 주요 엔티티 요약

### MeetingTranscript (MySQL → MongoDB 이전 예정)
- `fullText` (LONGTEXT), `summary`, `keywords` (JSON), `analyzedAt`
- `meeting_id`, `recording_id` 참조
- `segments` (OneToMany → MongoDB에서 내장 배열로 변환 예정)

### TranscriptSegment (MySQL → MongoDB 내장 예정)
- `speakerLabel`, `userId`, `content`, `startSec`, `endSec`, `sequence`

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
