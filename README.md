# MeetingApp Backend

회의 녹음을 자동으로 텍스트로 변환하고 AI가 요약, 할 일, 일정을 추출해주는 백엔드 서버입니다.

---

## 기술 스택

- **Spring Boot** - 백엔드 서버
- **MySQL** - 데이터 저장
- **AWS S3** - 음성 파일 저장
- **AssemblyAI** - 음성 → 텍스트 변환 (STT)
- **Gemini** - 텍스트 분석 (요약, 할 일, 일정 추출)
- **JWT** - 인증

---

## 전체 서비스 흐름

```
1. 회원가입 / 로그인 → JWT 토큰 발급
2. 회의 시작 → meetingId 발급
3. 음성 파일 S3 업로드 → recordingId 발급
4. AI 분석 요청 (meetingId + recordingId)
   ├── S3 Presigned URL 생성 (임시 다운로드 링크)
   ├── AssemblyAI에 URL 전달 → 음성을 텍스트로 변환
   └── Gemini에 텍스트 전달 → 요약 / 할 일 / 일정 추출 → MySQL 저장
5. 회의 종료
```

---

## AI 분석 상세 흐름

```
우리 서버                    AssemblyAI                    S3
    |                             |                          |
    | (1) Presigned URL 생성       |                          |
    |   recordingId로 DB 조회      |                          |
    |   → s3Key 꺼내서 임시 URL 생성 |                         |
    |                             |                          |
    | (2) "이 URL 분석해줘" POST →  |                          |
    |     audio_url 전달           |  (3) URL로 파일 다운로드 → |
    |                             |  ← 파일 받아서 STT 처리    |
    |                             |                          |
    | (4) "다 됐어?" GET (5초마다)   |                          |
    |    ← "아직..."               |                          |
    |    ← "완료! 텍스트 여기있어"   |                          |
    |                             |                          |
    ↓
  Gemini에게 텍스트 전달
    → 요약, 키워드, 할 일, 일정 JSON으로 반환
    → MySQL에 저장
```

### AI가 저장하는 데이터

| 테이블 | 저장 내용 |
|--------|---------|
| `MeetingTranscript` | 전체 텍스트, 요약, 키워드 |
| `TranscriptSegment` | 화자별 발언 (A: "안녕하세요", B: "네...") |
| `SpeakerMapping` | 화자 A = 홍길동 매핑 |
| `Task` | 할 일 목록 (담당자, 기한 포함) |
| `Event` | 일정 (시작/종료 시간 포함) |

---

## API 목록

### 인증

#### 회원가입
```
POST /user
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "password123",
  "displayName": "홍길동",
  "profileImageUrl": "https://..."   // 선택
}
```

#### 로그인
```
POST /login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "password123"
}
```
응답:
```json
{
  "accessToken": "eyJhbGci...",
  "refreshToken": "eyJhbGci..."
}
```

#### 토큰 갱신
```
POST /refresh
Content-Type: application/json

{
  "refreshToken": "eyJhbGci..."
}
```

#### 로그아웃
```
POST /logout
Content-Type: application/json

{
  "userId": 1
}
```

> 이후 모든 API는 Header에 `Authorization: Bearer {accessToken}` 필요

---

### 회의

#### 회의 시작
```
POST /api/meetings
Content-Type: application/json

{
  "workspaceId": 1,
  "channelId": 2,
  "title": "3월 정기 회의",
  "createdBy": 1
}
```
응답으로 `meetingId` 발급됨

#### 회의 종료
```
PATCH /api/meetings/{meetingId}/end
```

#### 회의 단건 조회
```
GET /api/meetings/{meetingId}
```

#### 회의 목록 조회
```
GET /api/meetings?workspaceId=1
GET /api/meetings?channelId=2
GET /api/meetings?workspaceId=1&channelId=2
```

#### 회의 삭제
```
DELETE /api/meetings/{meetingId}
```

---

### 녹음 파일

#### 방법 1 - 서버 경유 업로드 (파일을 서버가 받아서 S3에 올림)
```
POST /api/recordings/upload?meetingId=1
Content-Type: multipart/form-data

form-data:
  file: (음성 파일)
```
응답으로 `recordingId` 발급됨

#### 방법 2 - 클라이언트 직접 S3 업로드 (Presigned URL 방식)
1단계: 업로드용 URL 발급
```
POST /api/recordings/presigned-upload-url
Content-Type: application/json

{
  "meetingId": 1,
  "filename": "meeting_audio.mp3"
}
```
응답:
```json
{
  "presignedUrl": "https://s3.amazonaws.com/...PUT용 임시 URL...",
  "s3Key": "meetings/1/meeting_audio.mp3",
  "expiresAt": "2026-03-06T15:00:00"
}
```
2단계: 받은 URL로 직접 PUT 요청 (서버 거치지 않고 S3에 직접 업로드)
```
PUT {presignedUrl}
Content-Type: audio/mpeg
Body: (파일 바이너리)
```

#### 녹음 목록 조회
```
GET /api/recordings?meetingId=1
```

#### 녹음 다운로드 URL 발급 (유효시간 1시간)
```
GET /api/recordings/{recordingId}/presigned-url
```
응답:
```json
{
  "presignedUrl": "https://s3.amazonaws.com/...GET용 임시 URL...",
  "s3Key": "meetings/1/meeting_audio.mp3",
  "expiresAt": "2026-03-06T15:00:00"
}
```

#### 녹음 상태 변경
```
PATCH /api/recordings/{recordingId}/status?status=DONE
```
status 값: `UPLOADED` / `PROCESSING` / `DONE` / `ERROR`

#### 녹음 삭제 (S3 + DB 동시 삭제)
```
DELETE /api/recordings/{recordingId}
```

---

### AI 분석

회의 음성을 자동으로 분석해서 요약, 할 일, 일정을 생성합니다.

#### 분석 요청
```
POST /api/meetings/{meetingId}/analyze
Content-Type: application/json

{
  "recordingId": 1,
  "speakerMappings": [
    { "speakerLabel": "A", "userId": 1, "userName": "홍길동" },
    { "speakerLabel": "B", "userId": 2, "userName": "김철수" }
  ]
}
```

> `speakerLabel`은 AssemblyAI가 자동으로 붙이는 화자 구분 값입니다 (A, B, C...).
> 누가 A인지 B인지는 사람이 직접 매핑해줘야 합니다.

내부 처리 순서:
1. `recordingId`로 DB 조회 → S3 경로 확인
2. S3 Presigned URL 생성 (1시간짜리 임시 링크)
3. AssemblyAI에 URL 전달 → 음성을 텍스트로 변환 (최대 10분 대기)
4. Gemini에 텍스트 전달 → JSON으로 결과 반환
5. 요약, 할 일, 일정 MySQL에 저장

응답:
```
"회의 분석이 완료되었습니다. Task와 Event가 생성되었습니다."
```

---

### 트랜스크립트 (분석 결과 조회)

#### 트랜스크립트 조회
```
GET /api/meetings/{meetingId}/transcript
```

#### 화자 매핑 조회
```
GET /api/meetings/transcripts/{transcriptId}/speaker-mappings
```

#### 화자 매핑 저장/수정
```
PUT /api/meetings/transcripts/{transcriptId}/speaker-mappings
Content-Type: application/json

{
  "mappings": [
    { "speakerLabel": "A", "userId": 1 },
    { "speakerLabel": "B", "userId": 2 }
  ]
}
```

---

## 테스트 순서 (처음 테스트할 때)

```
1. POST /user               → 회원가입
2. POST /login              → 로그인 (accessToken 복사)
3. POST /api/meetings       → 회의 생성 (meetingId 복사)
4. POST /api/recordings/upload?meetingId={meetingId}  → 음성 파일 업로드 (recordingId 복사)
5. POST /api/meetings/{meetingId}/analyze             → AI 분석 요청 (시간 걸림)
6. GET  /api/meetings/{meetingId}/transcript          → 결과 확인
```

---

## 환경변수 설정

`.env` 또는 서버 환경변수에 아래 값들을 설정해야 합니다.

```
DATASOURCE_URL=jdbc:mysql://...
DATASOURCE_USERNAME=...
DATASOURCE_PASSWORD=...

AWS_ACCESS_KEY_ID=...
AWS_SECRET_ACCESS_KEY=...

ASSEMBLYAI_API_KEY=...
GEMINI_API_KEY=...

JWT_SECRET=...
```
