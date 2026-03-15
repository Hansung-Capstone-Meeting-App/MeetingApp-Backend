# MeetingApp Backend Postman Guide

## 테스트에 사용할 값
- `userId`: `12`
- `meetingId`: `11`
- `recordingId`: 분석에 사용할 최신 녹음 ID
- `transcriptId`: transcript 조회 응답의 `id` 값
- 서버 주소: `http://localhost:8080`

## 1. 회원가입
- Method: `POST`
- URL: `http://localhost:8080/user`
- Headers:
  - `Content-Type: application/json`
- Body 타입: `raw` + `JSON`

```json
{
  "email": "test@test.com",
  "password": "test1234!",
  "displayName": "테스트",
  "profileImageUrl": "https://picsum.photos/200"
}
```

## 2. 로그인
- Method: `POST`
- URL: `http://localhost:8080/login`
- 로그인 요청에는 `Authorization` 헤더를 넣지 않음
- Headers:
  - `Content-Type: application/json`
- Body 타입: `raw` + `JSON`

```json
{
  "email": "test@test.com",
  "password": "test1234!"
}
```

### 로그인 응답 예시
```json
{
  "accessToken": "eyJ...",
  "refreshToken": "eyJ...",
  "expiresIn": 3600
}
```

- 이후 `/api/...` 요청에서는 `accessToken` 사용
- Postman 설정:
  - `Authorization`
  - `Type: Bearer Token`
  - `Token` 칸에는 `Bearer ` 없이 토큰 문자열만 입력

## 3. 회의 생성
- Method: `POST`
- URL: `http://localhost:8080/api/meetings`
- Authorization: `Bearer Token`
- Body 타입: `raw` + `JSON`

```json
{
  "workspaceId": 1,
  "channelId": 1,
  "title": "화자분리 테스트",
  "createdBy": 12
}
```

### 응답 예시
```json
{
  "id": 11,
  "workspaceId": 1,
  "channelId": 1,
  "title": "화자분리 테스트",
  "startedAt": "2026-03-15T16:00:00",
  "endedAt": null,
  "createdBy": 12,
  "createdAt": "2026-03-15T16:00:00"
}
```

- 응답의 `id`가 `meetingId`

## 4. 녹음 파일 업로드
- Method: `POST`
- URL: `http://localhost:8080/api/recordings/upload?meetingId=11`
- Authorization: `Bearer Token`
- Body 타입: `form-data`
- 입력값
  - `Key`: `file`
  - `Type`: `File`
  - `Value`: 업로드할 `.m4a` 또는 `.mp3` 파일 선택

### 주의
- `raw` 아님
- `JSON` 아님
- `Content-Type: application/json` 헤더를 수동으로 넣지 않음

### 응답 예시
```json
{
  "recordingId": 17,
  "meetingId": 11,
  "s3Bucket": "hansung-capstone-2026",
  "s3Key": "recordings/11/uuid.m4a",
  "fileSize": 2106196,
  "durationSec": null,
  "status": "UPLOADED",
  "createdAt": "2026-03-15T16:15:26.3226837"
}
```

- 응답의 `recordingId`를 분석 요청에 사용

## 5. 회의 분석 요청
- Method: `POST`
- URL: `http://localhost:8080/api/meetings/11/analyze`
- Authorization: `Bearer Token`
- Body 타입: `raw` + `JSON`

```json
{
  "recordingId": 17,
  "speakerMappings": [
    {
      "speakerLabel": "A",
      "userId": 12,
      "userName": "테스트"
    }
  ]
}
```

### 성공 응답
```text
회의 분석이 완료되었습니다. 화자별 발화, 요약, 키워드, 일정이 저장되었습니다.
```

### 현재 분석 API가 실제로 처리하는 것
- AssemblyAI로 음성을 텍스트로 변환
- 화자별 발화 분리
- Gemini로 한국어 요약 생성
- 핵심 키워드 추출
- 할 일 추출 및 `tasks` 테이블 저장
- 일정 추출 및 `events`, `event_participants` 저장

## 6. 분석 결과 보기
- Method: `GET`
- URL: `http://localhost:8080/api/meetings/11/transcript`
- Authorization: `Bearer Token`
- Body 없음

### 응답에 포함되는 항목
- `id`: transcriptId
- `meetingId`
- `recordingId`
- `fullText`
- `summary`
- `keywords`
- `segments`

### 주의
- 이 API는 같은 `meetingId`에 transcript가 여러 개 있어도 가장 최근 transcript 1개를 반환함
- 이 API에는 `events`, `tasks`가 포함되지 않음

### segment 예시
```json
{
  "id": 198,
  "speakerLabel": "A",
  "userId": null,
  "content": "그러면 저희 지금 연기하는 거 맞죠?",
  "startSec": 2.478,
  "endSec": 6.938,
  "sequence": 0
}
```

## 7. 화자 매핑 조회
- Method: `GET`
- URL: `http://localhost:8080/api/meetings/transcripts/14/speaker-mappings`
- Authorization: `Bearer Token`

### 의미
- `speakerLabel`인 `A/B/C/D`가 실제 어떤 `userId`인지 조회
- 여기의 `14`는 `meetingId`가 아니라 `transcriptId`

### 응답 예시
```json
[
  {
    "id": 6,
    "transcriptId": 14,
    "speakerLabel": "A",
    "userId": 12
  }
]
```

## 8. 일정 조회
- Method: `GET`
- URL: `http://localhost:8080/api/events?meetingId=11`
- Authorization: `Bearer Token`

### 설명
- 분석 결과로 저장된 일정 목록 조회
- `meetingId` 기준 조회
- 필요하면 `workspaceId` 기준 조회도 가능

### 응답 예시
```json
[
  {
    "id": 7,
    "workspaceId": 1,
    "meetingId": 11,
    "title": "메신저 기능 배포",
    "description": "메신저 기능을 시스템에 배포",
    "location": null,
    "startAt": "2026-03-17T00:00:00",
    "endAt": "2026-03-17T23:59:59",
    "isAllDay": true,
    "createdBy": null,
    "color": "blue",
    "createdAt": "2026-03-15T16:17:45.849072",
    "participants": []
  }
]
```

## 9. 할 일 조회
- Method: `GET`
- URL: `http://localhost:8080/api/tasks?meetingId=11`
- Authorization: `Bearer Token`

### 설명
- 분석 결과로 저장된 할 일 목록 조회
- `meetingId` 기준 조회
- 필요하면 `workspaceId`, `assigneeId` 기준 조회도 가능

### 응답 예시
```json
[
  {
    "id": 3,
    "workspaceId": 1,
    "meetingId": 11,
    "assigneeId": 12,
    "createdBy": null,
    "title": "일정 추출 기능 구현",
    "description": "다음 주까지 일정 추출 기능을 구현한다.",
    "dueDate": null,
    "status": "TODO",
    "source": "AI_GENERATED",
    "createdAt": "2026-03-15T16:17:45.100000"
  }
]
```

## 현재 조회 구조 정리
- `GET /api/meetings/{meetingId}/transcript`
  - 전사, 요약, 키워드, 화자별 발화
- `GET /api/events?meetingId={meetingId}`
  - 일정
- `GET /api/tasks?meetingId={meetingId}`
  - 할 일

즉 현재는 분석 결과를 한 API에서 전부 합쳐서 보지 않고, 기능별로 나눠서 조회하는 구조입니다.

## 현재 구현 상태
- 구현됨
  - 화자 분리
  - 화자별 발화 저장
  - 한국어 요약
  - 키워드 추출
  - 할 일 추출 및 저장
  - 일정 추출 및 저장
  - 화자 매핑 저장 및 조회
  - 일정 조회 API
  - 할 일 조회 API
- 아직 없는 것
  - transcript + events + tasks를 한 번에 내려주는 통합 조회 API

## 토큰 사용 정리
- 계정 `email/password`로 로그인 가능
- `accessToken`이 만료되면 재발급 필요
- 현재 프로젝트 설정
  - `accessToken`: 1시간
  - `refreshToken`: 7일

## 다음에 다시 테스트할 때 빠른 순서
1. `POST /login`
2. `accessToken` 복사
3. Postman `Authorization`를 `Bearer Token`으로 설정
4. `POST /api/meetings`
5. `POST /api/recordings/upload`
6. `POST /api/meetings/{meetingId}/analyze`
7. `GET /api/meetings/{meetingId}/transcript`
8. `GET /api/events?meetingId={meetingId}`
9. `GET /api/tasks?meetingId={meetingId}`
