# MeetingApp — 프론트엔드 기능·API 정리

> 백엔드 기준 (2026-05). 상세 스키마는 Swagger `http://localhost:8080/swagger-ui/index.html`  
> 공통 인증·에러: [FRONTEND_GUIDE.md](./FRONTEND_GUIDE.md) · Notion 상세: [FRONTEND_NOTION_INTEGRATION.md](./FRONTEND_NOTION_INTEGRATION.md)

---

## 공통

```http
Authorization: Bearer {accessToken}
```

| 상황 | API |
|------|-----|
| accessToken 만료 (401) | `POST /api/auth/refresh` body: `{ "refreshToken": "..." }` |
| refreshToken 만료 | 로그인 화면으로 |

---

## 기능 체크리스트 (백엔드 구현 현황)

| # | 기능 | 상태 | 프론트 할 일 |
|---|------|------|-------------|
| 1 | 이메일 회원가입·로그인 | ✅ | 로그인 화면 |
| 2 | Google 소셜 로그인 | ✅ | OAuth → callback → JWT 저장 |
| 3 | Notion 소셜 로그인 | ✅ | 별도 로그인 화면 있을 때만 (`/notion/auth-url`) |
| 4 | 일반 로그인 후 Notion 연동 | ✅ | 설정: `link/auth-url` → `POST /notion/link` |
| 5 | 워크스페이스·초대·배지 | ✅ | 목록 + `GET /invitations/count` 폴링 |
| 6 | 녹음 업로드 (S3 Presigned) | ✅ | 업로드 %는 클라이언트, 이후 폴링 |
| 7 | 녹음 처리 진행 (STT·매핑·분석) | ✅ | **3~5초 폴링** (`/pipeline`) |
| 8 | STT·화자 매핑·Gemini 분석 | ✅ | 단계별 화면 |
| 9 | 할일·일정·칸반 | ✅ | CRUD + stats |
| 10 | Notion 캘린더 DB + 일정 보내기 | ✅ | DB 등록 후 **수동** sync API |
| 11 | 회의 PDF보내기 | ✅ | 분석 완료 후 다운로드 |
| 12 | 회의록 Notion 저장 | ✅ | meeting-notes DB 등록 후 export |

**백엔드에 없음 (프론트만 처리)**

- WebSocket / 푸시 알림
- STT 진행률 % (단계 enum만 제공)
- 일정 생성 시 Notion **자동** 동기화 (설정 토글은 프론트에서 sync API 호출로 구현)
- 요약만 단독 파일(txt/md) export — PDF·Notion에 요약 포함

---

## 1. 인증

### 1.1 이메일

| 동작 | Method | Path | Body |
|------|--------|------|------|
| 회원가입 | POST | `/api/user/register` | email, password, name |
| 로그인 | POST | `/api/auth/login` | email, password |
| 로그아웃 | POST | `/api/auth/logout` | refreshToken |
| 토큰 갱신 | POST | `/api/auth/refresh` | refreshToken |

응답 공통: `accessToken`, `refreshToken` (로컬 저장)

### 1.2 Google 로그인

```
1. GET  /api/oauth2/google/auth-url     → { authUrl }
2. 사용자 Google 동의 → redirect에 code
3. POST /api/oauth2/google/callback     → { code }  (또는 GET /google/callback?code=)
4. JWT 저장 → 메인 화면
```

### 1.3 Notion 로그인 (단독 계정)

```
1. GET  /api/oauth2/notion/auth-url
2. POST /api/oauth2/notion/callback     → { code }
```

→ Notion 연동 정보가 **자동 저장**됨. 캘린더/회의록 DB는 별도 등록 필요.

### 1.4 Google(또는 이메일) 로그인 + Notion 연동 (설정)

**설정 화면에서는 `/notion/auth-url` 쓰지 말 것** → `/notion/link/auth-url` 만 사용.

```
1. GET  /api/oauth2/notion/link/auth-url
2. Notion 허용 → code (callback 처리 — FRONTEND_NOTION_INTEGRATION.md 참고)
3. POST /api/oauth2/notion/link          Authorization: Bearer JWT
        body: { "code": "..." }
4. PUT  /api/oauth2/notion/calendar-database
        body: { "databaseUrl": "https://www.notion.so/..." }
5. PUT  /api/oauth2/notion/meeting-notes-database   (회의록 export용, 선택)
        body: { "databaseUrl": "..." }
```

Notion 연동 상태 전용 API는 **없음**. 로컬 캐시 + calendar-database 저장 성공 여부로 UI 분기.

---

## 2. 워크스페이스·초대

### 화면

- 워크스페이스 목록
- 워크스페이스 생성
- 받은 초대함
- 워크스페이스 홈 (멤버·초대)

### API

| 동작 | Method | Path |
|------|--------|------|
| 목록 | GET | `/api/workspaces` |
| 생성 | POST | `/api/workspaces` body: `{ "name": "..." }` |
| 삭제 (owner) | DELETE | `/api/workspaces/{id}` |
| 멤버 목록 | GET | `/api/workspaces/{id}/members` |
| 멤버 초대 | POST | `/api/workspaces/{id}/members` body: `{ "email": "..." }` |
| 나가기 | DELETE | `/api/workspaces/{id}/members/me` |
| 사용자 검색 | GET | `/api/user/search?q=검색어` |

### 초대·배지 (폴링 가능)

| 동작 | Method | Path | 비고 |
|------|--------|------|------|
| 초대 목록 | GET | `/api/invitations` | PENDING만 |
| **배지용 개수** | GET | `/api/invitations/count` | `{ "count": 2 }` — 30~60초 폴링 권장 |
| 수락 | POST | `/api/invitations/{id}/accept` | 204 |
| 거절 | POST | `/api/invitations/{id}/decline` | 204 |

푸시 알림 없음 → 앱 진입·목록 화면에서 `count` 폴링 또는 목록 진입 시 1회 조회.

---

## 3. 회의

| 동작 | Method | Path |
|------|--------|------|
| 목록 | GET | `/api/meetings?workspaceId={id}` |
| 단건 | GET | `/api/meetings/{id}` |
| 생성 | POST | `/api/meetings` body: `{ "title", "workspaceId" }` |
| 삭제 | DELETE | `/api/meetings/{id}` |
| 대시보드 요약 | GET | `/api/meetings/{id}/summary` | 분석 후 summary·keywords·taskStats |

---

## 4. 녹음 업로드 · 진행 표시 (폴링)

### 4.1 업로드 플로우

```
[1] POST /api/recordings/presigned-upload-url
    body: { "meetingId": 1, "filename": "meeting.m4a" }
    → { recordingId, presignedUrl, ... }

[2] PUT {presignedUrl}  (파일 바이너리, Content-Type 설정)
    → 진행률: XMLHttpRequest upload.onprogress (프론트)

[3] PATCH /api/recordings/{recordingId}/status?status=UPLOADED

[4] POST /api/meetings/{meetingId}/recordings/{recordingId}/transcribe
    → STT 시작 (응답 대기 또는 아래 폴링 병행)
```

대안: `POST /api/recordings/upload?meetingId=` multipart (서버 경유, Presigned 없음)

### 4.2 진행 폴링 (필수 UI)

**3~5초마다** 호출, `COMPLETE` 또는 `FAILED` 시 중지.

```http
GET /api/recordings/{recordingId}/pipeline
Authorization: Bearer ...
```

응답 예:

```json
{
  "recordingId": 5,
  "meetingId": 1,
  "recordingStatus": "PROCESSING",
  "phase": "TRANSCRIBING",
  "transcriptId": null,
  "mappingComplete": false,
  "analysisComplete": false,
  "errorMessage": null
}
```

### `phase` → UI 문구

| phase | 의미 |
|-------|------|
| `UPLOADING` | 업로드 중 |
| `UPLOADED` | 업로드 완료, 전사 전 |
| `TRANSCRIBING` | STT 진행 중 |
| `AWAITING_SPEAKER_MAPPING` | 화자 매핑 필요 |
| `READY_FOR_ANALYSIS` | Gemini 분석 가능 |
| `COMPLETE` | 분석까지 완료 |
| `FAILED` | 실패 |

간단 상태만 필요하면:

```http
GET /api/recordings/{recordingId}/status
→ { "status": "UPLOADING" | "UPLOADED" | "PROCESSING" | "DONE" | "FAILED" }
```

### 4.3 기타 녹음 API

| 동작 | Method | Path |
|------|--------|------|
| 회의별 목록 | GET | `/api/recordings?meetingId=` |
| 재생 URL | GET | `/api/recordings/{id}/presigned-url` |
| 삭제 | DELETE | `/api/recordings/{id}` |

---

## 5. 트랜스크립트 · 화자 매핑 · AI 분석

| 단계 | Method | Path |
|------|--------|------|
| 트랜스크립트 조회 | GET | `/api/meetings/{meetingId}/transcript` |
| 화자 매핑 조회 | GET | `/api/meetings/transcripts/{transcriptId}/speaker-mappings` |
| 화자 매핑 저장 | PUT | `/api/meetings/transcripts/{transcriptId}/speaker-mappings` |
| Gemini 분석 | POST | `/api/meetings/transcripts/{transcriptId}/gemini-analyze` |

**화자 매핑 body** (userId 필수):

```json
[
  { "speakerLabel": "SPEAKER_00", "userId": 3, "userName": "홍길동" }
]
```

분석 재실행 시 AI 생성 할일·일정은 서버에서 삭제 후 재생성.

분석 중·후에도 `GET .../pipeline` 폴링 → `READY_FOR_ANALYSIS` → `COMPLETE`.

---

## 6. 할일 (Task)

| 동작 | Method | Path |
|------|--------|------|
| 목록 | GET | `/api/tasks?workspaceId=&meetingId=&assigneeId=&status=&dueBefore=` |
| 통계 (칸반) | GET | `/api/tasks/stats?workspaceId=&meetingId=` |
| 단건 | GET | `/api/tasks/{id}` |
| 생성 | POST | `/api/tasks` |
| 수정 | PATCH | `/api/tasks/{id}` | 부분 업데이트 |
| 삭제 | DELETE | `/api/tasks/{id}` |

`status`: `TODO` | `IN_PROGRESS` | `DONE`

---

## 7. 일정 (Event)

| 동작 | Method | Path |
|------|--------|------|
| 목록 | GET | `/api/events?workspaceId=&meetingId=` |
| 단건 | GET | `/api/events/{id}` | relatedTasks 포함 |
| 생성 | POST | `/api/events` |
| 수정 | PATCH | `/api/events/{id}` |
| 삭제 | DELETE | `/api/events/{id}` |

---

## 8. Notion — 일정 보내기

사전 조건: Notion 연동 + `PUT /notion/calendar-database`

| 동작 | Method | Path |
|------|--------|------|
| 단건 동기화 | POST | `/api/calendar/events/{eventId}/notion-sync` |
| 워크스페이스 일괄 | POST | `/api/calendar/workspaces/{workspaceId}/notion-sync` |
| ID 목록 일괄 | POST | `/api/calendar/events/notion-sync-batch` body: `{ "eventIds": [1,2] }` |
| 워크스페이스 이벤트 조회 | GET | `/api/calendar/workspaces/{workspaceId}/events` |

자동 동기화는 백엔드에 없음 → 프론트 `autoSync` 설정 시 이벤트 POST/PATCH 성공 후 위 API 호출.

---

## 9. 회의보내기 (PDF · Notion 회의록)

**조건:** Gemini 분석 완료 (`summary` 존재). 미완료 시 400.

| 동작 | Method | Path | 비고 |
|------|--------|------|------|
| PDF 다운로드 | GET | `/api/meetings/{id}/export/pdf?includeEvents=true` | `application/pdf` 바이너리 |
| Notion 회의록 | POST | `/api/meetings/{id}/notion-export?includeEvents=true` | 재전송 시 같은 페이지 갱신 |

Notion 회의록 사전 조건:

1. Notion 연동 (`POST /notion/link` 또는 Notion 로그인)
2. `PUT /api/oauth2/notion/meeting-notes-database`

Notion export 응답:

```json
{
  "meetingId": 1,
  "notionPageId": "...",
  "notionUrl": "https://www.notion.so/...",
  "updated": false
}
```

요약만 JSON으로 보기: `GET /api/meetings/{id}/summary` (파일 export 아님).

---

## 10. 프로필

| 동작 | Method | Path |
|------|--------|------|
| 조회 | GET | `/api/user/profile` |
| 이름 | PATCH | `/api/user/profile/name` |
| 이미지 | PATCH | `/api/user/profile/image` |
| 비밀번호 | PATCH | `/api/user/password` |
| 탈퇴 | DELETE | `/api/user/account` |
| 이미지 Presigned | GET | `/api/user/presigned-url?filename=` → PUT S3 → PATCH image |

---

## 11. 화면 ↔ API 매핑

```
로그인/회원가입
  ├─ 이메일: /api/auth/login, /api/user/register
  ├─ Google: /api/oauth2/google/*
  └─ Notion: /api/oauth2/notion/* (선택)

워크스페이스 목록
  ├─ GET /api/workspaces
  └─ 배지: GET /api/invitations/count (주기 폴링)

받은 초대함
  └─ GET/POST /api/invitations/*

설정 > Notion
  └─ FRONTEND_NOTION_INTEGRATION.md

회의 상세
  ├─ 업로드 + pipeline 폴링
  ├─ 화자 매핑
  ├─ Gemini 분석
  ├─ PDF: GET .../export/pdf
  └─ Notion: POST .../notion-export

할일/캘린더
  ├─ /api/tasks/*
  ├─ /api/events/*
  └─ Notion sync: /api/calendar/.../notion-sync
```

---

## 12. 전체 사용자 플로우

```
로그인
  → 워크스페이스 (초대 수락 가능)
  → 회의 생성
  → 녹음 업로드 (S3) → status=UPLOADED
  → transcribe 호출 → pipeline 폴링 (TRANSCRIBING…)
  → 화자 매핑 (AWAITING_SPEAKER_MAPPING)
  → gemini-analyze (READY_FOR_ANALYSIS → COMPLETE)
  → 요약·할일·일정 확인/수정
  → (선택) PDF / Notion 회의록 / Notion 일정 sync
```

---

## 13. 주요 에러 (프론트 메시지)

| HTTP | 예시 | UI |
|------|------|-----|
| 401 | 토큰 만료 | refresh 후 재시도 |
| 403 | 멤버 아님, Notion 미연동 | 권한 안내 |
| 400 | MEETING_EXPORT_NOT_READY | 「AI 분석 완료 후 가능」 |
| 400 | NOTION_MEETING_NOTES_DB_NOT_REGISTERED | DB 등록 유도 |
| 409 | ALREADY_INVITED | 토스트 |

에러 body: `{ "status": 400, "message": "..." }`

---

## 14. 프론트 폴링 권장 요약

| 대상 | API | 주기 | 중지 조건 |
|------|-----|------|-----------|
| 초대 배지 | `GET /api/invitations/count` | 30~60초 | count 변화 시 UI만 갱신 |
| 녹음 파이프라인 | `GET /api/recordings/{id}/pipeline` | 3~5초 | phase ∈ COMPLETE, FAILED |
| (대안) 녹음 상태 | `GET /api/recordings/{id}/status` | 3~5초 | status ∈ DONE, FAILED |

S3 업로드 %는 **폴링 아님** — 브라우저 upload progress 이벤트 사용.
