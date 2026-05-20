# MeetingApp Backend 코드 분석 정리

분석일: 2026-05-13

## 1. 프로젝트 개요

이 프로젝트는 Spring Boot 기반 회의 앱 백엔드입니다.

주요 기능은 다음과 같습니다.

- 사용자 회원가입, 로그인, JWT 인증
- 회의 생성, 종료, 조회, 삭제
- 회의 녹음 파일 S3 업로드
- S3 Presigned URL 발급
- AssemblyAI를 이용한 STT 처리
- Gemini를 이용한 회의 내용 요약, 키워드, 할 일, 일정 추출
- AI 분석 결과를 DB에 저장하고 조회

즉, 회의 녹음 파일을 업로드하면 AI가 회의록을 만들고, 그 안에서 할 일과 일정을 자동으로 뽑아내는 백엔드입니다.

## 2. 기술 스택

- Java 17
- Spring Boot 3.2.5
- Spring Web
- Spring WebFlux
- Spring Security
- Spring Data JPA
- MySQL
- H2
- Thymeleaf
- Swagger / OpenAPI
- AWS SDK v2 S3
- JWT
- Lombok
- AssemblyAI
- Gemini

## 3. 전체 구조

패키지는 크게 다음 도메인으로 나뉩니다.

```text
com.capston.demo
├── domain
│   ├── user
│   ├── meeting
│   ├── recording
│   ├── ai
│   └── calender
└── global
    ├── config
    ├── exception
    └── util
```

`domain` 아래에는 실제 비즈니스 기능이 들어가고, `global` 아래에는 보안, 설정, 예외 처리, 공통 유틸이 들어갑니다.

## 4. 사용자 / 인증 도메인

사용자 도메인은 회원가입, 로그인, 토큰 재발급, 로그아웃을 담당합니다.

주요 파일:

- `AuthController`
- `UserController`
- `AuthService`
- `UserService`
- `MyUserDetailsService`
- `User`
- `RefreshToken`
- `UserRepository`
- `RefreshTokenRepository`
- `JwtUtil`
- `JwtAuthenticationFilter`
- `SecurityConfig`

### 인증 흐름

1. 사용자가 `/user` 또는 `/register`로 회원가입합니다.
2. `/login`으로 이메일과 비밀번호를 보냅니다.
3. 서버는 비밀번호를 검증한 뒤 access token과 refresh token을 발급합니다.
4. refresh token은 DB에 저장됩니다.
5. 이후 API 요청은 `Authorization: Bearer {accessToken}` 헤더를 사용합니다.
6. `JwtAuthenticationFilter`가 토큰을 검증하고 SecurityContext에 인증 정보를 넣습니다.
7. `/refresh` 요청 시 refresh token을 검증하고 새 access token을 발급합니다.
8. `/logout` 요청 시 DB에 저장된 refresh token을 삭제합니다.

### 주의할 점

현재 `/logout`이 `permitAll`로 열려 있고, 요청 body의 `userId` 기준으로 로그아웃을 처리하는 구조입니다.
이 방식은 다른 사용자의 `userId`를 넣어 refresh token을 삭제할 위험이 있습니다.
로그아웃은 인증된 사용자 정보에서 userId를 꺼내 처리하는 방식이 더 안전합니다.

## 5. 회의 도메인

회의 도메인은 회의 생성, 종료, 조회, 삭제와 회의록 저장/조회 기능을 담당합니다.

주요 파일:

- `MeetingController`
- `MeetingService`
- `MeetingTranscriptService`
- `Meeting`
- `MeetingRecording`
- `MeetingTranscript`
- `TranscriptSegment`
- `SpeakerMapping`
- `MeetingRepository`
- `MeetingTranscriptRepository`
- `TranscriptSegmentRepository`
- `SpeakerMappingRepository`

### 회의 기본 기능

지원 API:

- `POST /api/meetings`
- `PATCH /api/meetings/{id}/end`
- `GET /api/meetings/{id}`
- `GET /api/meetings?workspaceId=&channelId=`
- `DELETE /api/meetings/{id}`

회의를 생성하면 `Meeting` 엔티티에 다음 정보가 저장됩니다.

- workspaceId
- channelId
- title
- createdBy
- startedAt
- createdAt

회의 종료 시에는 `endedAt`이 기록됩니다.

### 회의록 기능

지원 API:

- `POST /api/meetings/{meetingId}/transcript`
- `GET /api/meetings/{meetingId}/transcript`
- `PUT /api/meetings/transcripts/{transcriptId}/speaker-mappings`
- `GET /api/meetings/transcripts/{transcriptId}/speaker-mappings`

회의록은 `MeetingTranscript`에 저장되고, 발화 단위는 `TranscriptSegment`에 저장됩니다.
화자 라벨과 실제 사용자 매핑은 `SpeakerMapping`에 저장됩니다.

예를 들어 AssemblyAI가 `A`, `B`, `C` 같은 speaker label을 반환하면, 나중에 이 라벨을 실제 userId와 연결할 수 있습니다.

## 6. 녹음 도메인

녹음 도메인은 S3 업로드와 Presigned URL 발급을 담당합니다.

주요 파일:

- `RecordingController`
- `RecordingService`
- `S3Util`
- `S3Config`
- `MeetingRecording`
- `MeetingRecordingRepository`

지원 API:

- `POST /api/recordings/upload?meetingId=`
- `GET /api/recordings?meetingId=`
- `PATCH /api/recordings/{recordingId}/status?status=`
- `POST /api/recordings/presigned-upload-url`
- `GET /api/recordings/{recordingId}/presigned-url`
- `DELETE /api/recordings/{recordingId}`

### 업로드 방식

업로드 방식은 두 가지입니다.

1. 서버 경유 업로드

   클라이언트가 파일을 서버로 보내고, 서버가 S3에 업로드합니다.
   이 경우 `MeetingRecording` 레코드가 바로 생성됩니다.

2. Presigned URL 직접 업로드

   서버가 S3 업로드용 Presigned PUT URL을 발급하고, 클라이언트가 S3에 직접 업로드합니다.

### 주의할 점

현재 Presigned URL 직접 업로드 방식은 URL만 발급합니다.
업로드 완료 후 `MeetingRecording` 레코드를 생성하는 confirm API가 명확하지 않습니다.

즉, 클라이언트가 S3에 직접 올린 뒤 AI 분석에 필요한 `recordingId`를 어떻게 얻는지 흐름이 부족합니다.

## 7. AI 분석 도메인

AI 도메인은 이 프로젝트의 핵심 기능입니다.

주요 파일:

- `MeetingAnalysisController`
- `MeetingAnalysisService`
- `AssemblyAiService`
- `GeminiAiService`
- `AssemblyAiTranscriptResult`
- `GeminiAnalysisResult`
- `MeetingAnalysisRequest`

지원 API:

- `POST /api/meetings/{meetingId}/analyze`

### AI 분석 흐름

1. 클라이언트가 `meetingId`, `recordingId`, `speakerMappings`를 보냅니다.
2. 서버는 `meetingId`로 회의를 조회합니다.
3. 서버는 `recordingId`로 녹음 정보를 조회합니다.
4. S3 Presigned GET URL을 생성합니다.
5. AssemblyAI에 `audio_url`로 Presigned URL을 전달합니다.
6. AssemblyAI가 STT를 수행하고 speaker label이 포함된 발화 목록을 반환합니다.
7. STT 결과와 speaker mapping 정보를 Gemini에 전달합니다.
8. Gemini가 summary, keywords, tasks, events를 JSON으로 반환합니다.
9. 서버는 결과를 DB에 저장합니다.
10. 녹음 상태를 `DONE`으로 변경합니다.

### 저장되는 데이터

AI 분석 결과는 다음 테이블에 나뉘어 저장됩니다.

- `meeting_transcripts`
- `transcript_segments`
- `speaker_mappings`
- `tasks`
- `events`
- `event_participants`

### 중요한 문제점

현재 AI 분석은 하나의 HTTP 요청 안에서 동기적으로 처리됩니다.

AssemblyAI polling은 최대 120회, 5초 간격으로 동작합니다.
최대 10분 동안 요청이 블로킹될 수 있습니다.

또한 이 로직이 `@Transactional` 안에서 실행되기 때문에 외부 API 호출 중에도 DB 트랜잭션이 오래 유지됩니다.
실서비스 구조라면 비동기 작업으로 분리하는 것이 좋습니다.

권장 구조:

1. 분석 요청 API는 작업만 생성하고 바로 응답합니다.
2. 백그라운드 작업이 AssemblyAI와 Gemini 호출을 처리합니다.
3. 클라이언트는 분석 상태 조회 API로 진행 상태를 확인합니다.
4. 완료되면 transcript, task, event를 조회합니다.

## 8. 캘린더 / 할 일 도메인

패키지명은 `calender`로 되어 있습니다.
영어 표기는 보통 `calendar`가 맞습니다.

주요 파일:

- `EventController`
- `TaskController`
- `EventService`
- `TaskService`
- `Event`
- `Task`
- `EventParticipant`
- `EventRepository`
- `TaskRepository`

지원 API:

- `GET /api/events?meetingId=`
- `GET /api/events?workspaceId=`
- `GET /api/tasks?meetingId=`
- `GET /api/tasks?workspaceId=`
- `GET /api/tasks?assigneeId=`

현재는 조회 기능 위주입니다.
Task와 Event 생성은 주로 AI 분석 결과 저장 과정에서 이루어집니다.

## 9. 주요 엔티티 관계

### Meeting

회의의 중심 엔티티입니다.

관계:

- Meeting 1:N MeetingRecording
- Meeting 1:N MeetingTranscript

### MeetingRecording

S3에 저장된 녹음 파일 정보를 가집니다.

주요 필드:

- s3Bucket
- s3Key
- fileSize
- durationSec
- status

### MeetingTranscript

회의 전체 STT 결과와 AI 요약을 저장합니다.

주요 필드:

- fullText
- summary
- keywords
- analyzedAt

관계:

- MeetingTranscript 1:N TranscriptSegment
- MeetingTranscript 1:N SpeakerMapping

### TranscriptSegment

발화 단위 데이터입니다.

주요 필드:

- speakerLabel
- userId
- content
- startSec
- endSec
- sequence

### SpeakerMapping

AI가 구분한 speaker label과 실제 userId를 연결합니다.

예시:

```text
A -> userId 1
B -> userId 2
```

### Task

회의에서 나온 할 일을 저장합니다.

주요 필드:

- workspaceId
- assigneeId
- title
- description
- dueDate
- status
- source
- meetingId

### Event

회의에서 나온 일정을 저장합니다.

주요 필드:

- workspaceId
- title
- description
- location
- startAt
- endAt
- isAllDay
- createdBy
- meetingId
- color

## 10. 설정 파일 분석

설정 파일:

- `src/main/resources/application.yml`

필요한 환경변수:

```text
DATASOURCE_URL
DATASOURCE_USERNAME
DATASOURCE_PASSWORD
AWS_ACCESS_KEY_ID
AWS_SECRET_ACCESS_KEY
ASSEMBLYAI_API_KEY
GEMINI_API_KEY
JWT_SECRET
```

주요 설정:

- DB는 MySQL 사용
- JPA `ddl-auto`는 `update`
- S3 bucket은 `hansung-capstone-2026`
- AWS region은 `ap-northeast-2`
- Gemini 모델은 `gemini-2.5-flash`
- access token 만료 시간은 1시간
- refresh token 만료 시간은 7일

## 11. 장점

- 도메인별 패키지 구조가 명확합니다.
- 회의, 녹음, AI 분석, 일정/할 일이 기능별로 나뉘어 있습니다.
- S3 업로드 방식이 서버 경유 방식과 Presigned URL 방식 둘 다 지원됩니다.
- STT 결과를 전체 텍스트와 발화 단위로 나누어 저장합니다.
- speaker label과 userId 매핑 구조가 따로 있어 실제 사용자 연결이 가능합니다.
- AI 분석 결과가 Task와 Event로 저장되어 프론트에서 활용하기 좋습니다.
- Swagger 문서화를 위한 controllerDocs 구조가 있습니다.

## 12. 문제점 / 리스크

### 1. 인코딩 깨짐

README, 주석, 예외 메시지, 기존 transcript 데이터 일부가 깨져 있습니다.
UTF-8 기준으로 다시 정리하는 것이 필요합니다.

### 2. 예외 처리 범위 문제

`GlobalExceptionHandler`가 일반 `Exception`을 전부 500으로 처리합니다.

그 결과 `IllegalArgumentException` 같은 클라이언트 요청 오류도 500으로 내려갈 수 있습니다.
400 Bad Request로 분리하는 핸들러가 필요합니다.

### 3. AI 분석이 동기식

AssemblyAI와 Gemini 호출이 한 요청 안에서 처리됩니다.
요청 시간이 길어지고, 장애가 나면 사용자 경험이 나빠집니다.

### 4. 트랜잭션이 너무 김

`MeetingAnalysisService.analyze()`가 `@Transactional`인데, 그 안에서 외부 API 호출까지 수행합니다.
DB 트랜잭션은 DB 작업 구간에만 짧게 유지하는 것이 좋습니다.

### 5. Presigned 직접 업로드 후속 흐름 부족

Presigned PUT URL 발급 후 실제 업로드가 끝났을 때 DB에 `MeetingRecording`을 생성하는 API가 없습니다.

### 6. 로그아웃 보안 문제

요청으로 받은 userId를 기준으로 refresh token을 삭제합니다.
인증된 사용자 기준으로 삭제해야 더 안전합니다.

### 7. 테스트 부족

현재 테스트는 `contextLoads` 하나뿐입니다.

추가로 필요한 테스트:

- 회원가입 테스트
- 로그인 테스트
- JWT 인증 테스트
- 회의 생성/종료 테스트
- 녹음 업로드 테스트
- transcript 저장/조회 테스트
- speaker mapping 테스트
- AI 분석 서비스 테스트

### 8. 패키지명 오타

`calender`는 오타입니다.
장기적으로는 `calendar`로 변경하는 것이 좋습니다.

## 13. 개선 우선순위

### 1순위

- 깨진 한글 인코딩 복구
- 예외 메시지 정리
- `IllegalArgumentException`을 400으로 반환하도록 수정
- 로그아웃을 인증 사용자 기준으로 변경

### 2순위

- AI 분석을 비동기 작업으로 분리
- 분석 상태 enum 추가
- 분석 요청, 진행 중, 완료, 실패 상태 조회 API 추가
- Presigned 업로드 완료 confirm API 추가

### 3순위

- 테스트 코드 추가
- `calender` 패키지명 정리
- README 재작성
- Swagger 문서 보강
- Task/Event 중복 생성 방지 로직 추가

## 14. 추천 API 흐름

실제 사용 흐름은 다음과 같이 잡는 것이 좋습니다.

```text
1. POST /user
2. POST /login
3. POST /api/meetings
4. POST /api/recordings/upload
5. POST /api/meetings/{meetingId}/analyze
6. GET /api/meetings/{meetingId}/transcript
7. GET /api/tasks?meetingId={meetingId}
8. GET /api/events?meetingId={meetingId}
```

Presigned 직접 업로드를 쓴다면 다음 API가 추가로 필요합니다.

```text
1. POST /api/recordings/presigned-upload-url
2. PUT {presignedUrl}
3. POST /api/recordings/confirm
4. POST /api/meetings/{meetingId}/analyze
```

현재는 3번 confirm API가 없으므로 직접 업로드 흐름은 완성도가 부족합니다.

## 15. 결론

이 백엔드는 회의 녹음 기반 AI 회의록 앱의 핵심 구조를 갖추고 있습니다.

현재 가장 중요한 개선 포인트는 세 가지입니다.

1. 깨진 한글 문서와 메시지 복구
2. AI 분석 작업의 비동기화
3. 인증/예외 처리 안정화

이 세 가지를 먼저 정리하면, 이후에는 테스트와 API 문서화를 추가하면서 서비스 품질을 높이기 쉬운 구조입니다.
