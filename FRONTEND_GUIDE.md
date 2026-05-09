# MeetingApp 프론트엔드 개발 가이드

> 백엔드 API 기준 작성. 상세 요청/응답 스펙은 Swagger UI(`/swagger-ui/index.html`) 참고.

---

## 공통 사항

### 인증
모든 API 요청(로그인·회원가입 제외)에 헤더 포함 필수.
```
Authorization: Bearer {accessToken}
```

### 토큰 관리
- 로그인 응답으로 `accessToken`, `refreshToken` 수신
- `accessToken` 만료(401) 시 → `POST /api/auth/refresh` 로 재발급
- `refreshToken` 도 만료되면 → 로그인 화면으로 이동

### 에러 응답 형식
```json
{ "status": 404, "message": "회의를 찾을 수 없습니다" }
```

---

## 전체 화면 목록

```
로그인 / 회원가입
└── 워크스페이스 목록
    ├── 워크스페이스 생성
    ├── 받은 초대함
    └── 워크스페이스 홈
        ├── 멤버 목록 / 초대
        ├── 회의 목록
        │   ├── 회의 생성
        │   └── 회의 상세
        │       ├── 녹음 업로드 → STT 진행
        │       ├── 화자 매핑
        │       ├── AI 분석 결과
        │       └── 회의 요약 (할일·일정 수 포함)
        ├── 할일 목록 (칸반 or 리스트)
        └── 캘린더 (일정 목록)
```

---

## 화면별 시나리오

---

### 1. 로그인 / 회원가입

**회원가입**
1. 이메일·비밀번호·이름 입력
2. `POST /api/user/register`
3. 성공 → 로그인 화면 이동

**이메일 로그인**
1. `POST /api/auth/login`
2. 응답의 `accessToken`, `refreshToken` 로컬 저장
3. 워크스페이스 목록 화면 이동

**Google 로그인**
1. Google OAuth 인가 코드 획득
2. `POST /api/oauth2/google` 에 코드 전송
3. 이후 동일하게 토큰 저장 후 이동

**로그아웃**
- `POST /api/auth/logout` (refreshToken 전송)
- 로컬 토큰 삭제 후 로그인 화면 이동

---

### 2. 워크스페이스 목록

**진입 시**
1. `GET /api/workspaces` → 내가 속한 워크스페이스 목록 표시
2. 상단 배지: `GET /api/invitations` 로 PENDING 초대 수 표시

**워크스페이스 생성**
1. 이름 입력
2. `POST /api/workspaces`
3. 성공 → 목록 갱신

---

### 3. 받은 초대함

**진입 시**
- `GET /api/invitations` → PENDING 상태 초대 목록 표시
- 각 항목: 워크스페이스 이름, 초대한 사람 이름, 받은 날짜

**수락**
1. `POST /api/invitations/{invitationId}/accept`
2. 성공 → 해당 워크스페이스 멤버로 자동 등록됨
3. 워크스페이스 목록 갱신

**거절**
1. `POST /api/invitations/{invitationId}/decline`
2. 목록에서 제거

---

### 4. 워크스페이스 홈

**멤버 목록 조회**
- `GET /api/workspaces/{workspaceId}/members`
- 역할(owner / admin / member) 표시

**멤버 초대**
1. 이름 or 이메일 검색창 → `GET /api/user/search?q=검색어`
2. 결과 드롭다운에서 사용자 선택
3. `POST /api/workspaces/{workspaceId}/members` (선택된 사용자의 email 전송)
4. 성공 → 초대 발송 완료 토스트 표시 (상대방이 수락해야 멤버로 등록됨)

**워크스페이스 나가기** (owner 제외)
- `DELETE /api/workspaces/{workspaceId}/members/me`
- 성공 → 워크스페이스 목록으로 이동

**워크스페이스 삭제** (owner 전용)
- `DELETE /api/workspaces/{workspaceId}`
- 성공 → 워크스페이스 목록으로 이동

---

### 5. 회의 목록

**진입 시**
- `GET /api/meetings?workspaceId={workspaceId}` → 회의 목록 표시

**회의 생성**
1. 제목 입력
2. `POST /api/meetings` (body: `{ "title": "...", "workspaceId": ... }`)
3. 성공 → 회의 상세 화면 이동

**회의 삭제**
- `DELETE /api/meetings/{meetingId}`

---

### 6. 회의 상세

회의 하나에 여러 녹음 파일이 붙는 구조.

**진입 시**
- `GET /api/meetings/{meetingId}` → 회의 기본 정보
- `GET /api/meetings/{meetingId}/summary` → 요약 정보 (분석 완료된 경우)

---

### 7. 녹음 업로드 → STT

> 흐름이 여러 단계이므로 로딩 상태 관리 필요.

```
[1] Presigned URL 발급
    POST /api/recordings/presigned-upload-url
    body: { "meetingId": 1, "fileName": "meeting.m4a", "contentType": "audio/mp4" }
    응답: { "recordingId": 5, "presignedUrl": "https://s3.amazonaws.com/..." }

[2] S3 직접 업로드
    PUT {presignedUrl}
    body: 파일 바이너리 (Content-Type 헤더 포함)
    → 백엔드 없이 S3에 직접 업로드

[3] 상태 업데이트
    PATCH /api/recordings/5/status
    body: { "status": "UPLOADED" }

[4] STT 시작
    POST /api/meetings/{meetingId}/recordings/5/transcribe
    → 처리 시간 소요 (수십 초~수 분)
    → 완료 응답 오면 트랜스크립트 화면으로 이동
```

**STT 완료 후 트랜스크립트 조회**
- `GET /api/meetings/{meetingId}/transcript`
- 화자 레이블(SPEAKER_A, SPEAKER_B...) + 발화 내용 표시

---

### 8. 화자 매핑

STT 결과의 화자 레이블(SPEAKER_A 등)을 실제 사람으로 연결하는 단계.

**진입 시**
1. `GET /api/workspaces/{workspaceId}/members` → 선택 가능한 멤버 목록
2. `GET /api/meetings/transcripts/{transcriptId}/speaker-mappings` → 기존 매핑 불러오기 (있으면)

**매핑 저장**
```
PUT /api/meetings/transcripts/{transcriptId}/speaker-mappings
body:
[
  { "speakerLabel": "SPEAKER_A", "userId": 3, "userName": "홍길동" },
  { "speakerLabel": "SPEAKER_B", "userId": 5, "userName": "김철수" }
]
```

> 매핑 저장 후 바로 AI 분석을 실행해야 매핑된 이름이 결과에 반영됨.

---

### 9. AI 분석 (Gemini)

**분석 실행**
```
POST /api/meetings/transcripts/{transcriptId}/gemini-analyze
→ 처리 시간 소요
→ 완료 시 할일·일정이 자동 생성됨
```

> 재실행 가능 — 기존 AI 생성 할일·일정은 자동 삭제 후 새로 생성됨.

**분석 완료 후 표시할 데이터**
- `GET /api/meetings/{meetingId}/transcript` → 화자 이름이 반영된 전체 대화록
- `GET /api/tasks?meetingId={meetingId}` → AI가 추출한 할일 목록
- `GET /api/events?workspaceId={workspaceId}` → AI가 추출한 일정 목록
- `GET /api/meetings/{meetingId}/summary` → 요약·키워드·할일 통계

---

### 10. 할일 (Task)

**목록 조회**
```
GET /api/tasks?workspaceId=1           // 워크스페이스 전체 할일
GET /api/tasks?meetingId=3             // 특정 회의 할일
GET /api/tasks?assigneeId=5            // 특정 사람 할일
GET /api/tasks?workspaceId=1&status=TODO       // 상태 필터
GET /api/tasks?workspaceId=1&dueBefore=2026-06-01  // 마감일 필터
```

**칸반 통계** (칸반 보드 상단 수치용)
```
GET /api/tasks/stats?workspaceId=1
응답: { "total": 10, "todo": 4, "inProgress": 3, "done": 3 }
```

**할일 생성** (사용자 직접 등록)
```
POST /api/tasks
body: {
  "title": "기획서 작성",
  "assigneeId": 3,
  "assigneeName": "홍길동",
  "dueDate": "2026-06-01",
  "workspaceId": 1,
  "meetingId": 2   // 선택
}
```

**할일 수정**
```
PATCH /api/tasks/{id}
body: { "status": "IN_PROGRESS" }  // 변경할 필드만
```

**할일 삭제**
- `DELETE /api/tasks/{id}`

**status 값**: `TODO` / `IN_PROGRESS` / `DONE`

---

### 11. 일정 (Event)

**목록 조회**
```
GET /api/events?workspaceId=1
응답에 relatedTasks 포함 — 해당 회의의 할일 목록과 상태 같이 반환
```

**일정 생성**
```
POST /api/events
body: {
  "title": "스프린트 회의",
  "startAt": "2026-06-01T10:00:00",
  "endAt": "2026-06-01T11:00:00",
  "workspaceId": 1,
  "participantUserIds": [3, 5]   // 선택
}
```

**일정 수정**
```
PATCH /api/events/{id}
body: { "title": "변경된 제목" }  // 변경할 필드만
```

**일정 삭제**
- `DELETE /api/events/{id}`

---

### 12. 프로필

| 기능 | API |
|------|-----|
| 프로필 조회 | `GET /api/user/profile` |
| 이름 변경 | `PATCH /api/user/profile/name` |
| 프로필 이미지 변경 | `PATCH /api/user/profile/image` |
| 비밀번호 변경 | `PATCH /api/user/password` |
| 회원 탈퇴 | `DELETE /api/user/account` |

**프로필 이미지 업로드 흐름**
1. `GET /api/user/presigned-url?filename=profile.jpg` → presignedUrl 수신
2. `PUT {presignedUrl}` 로 S3 직접 업로드
3. `PATCH /api/user/profile/image` 에 S3 URL 저장

---

## 주요 에러 코드 정리

| HTTP | 의미 | 대응 |
|------|------|------|
| 400 | 잘못된 요청 (필수 필드 누락 등) | 메시지 표시 |
| 401 | 토큰 만료 | `/api/auth/refresh` 후 재요청 |
| 403 | 권한 없음 (멤버 아님, owner 아님 등) | "권한이 없습니다" 표시 |
| 404 | 리소스 없음 | "찾을 수 없습니다" 표시 |
| 409 | 중복 (이미 멤버, 이미 초대됨 등) | 메시지 그대로 표시 |

---

## 전체 플로우 요약

```
회원가입 / 로그인
    ↓
워크스페이스 생성 or 초대 수락
    ↓
회의 생성
    ↓
녹음 파일 업로드 → S3 직접 업로드 → STT 시작
    ↓
화자 매핑 (워크스페이스 멤버 중 선택)
    ↓
AI 분석 실행 (Gemini)
    ↓
결과 확인:
  - 회의 요약 · 키워드
  - 화자별 대화록
  - 할일 목록 (수정 · 삭제 · 추가 가능)
  - 일정 목록 (수정 · 삭제 · 추가 가능)
    ↓
캘린더에서 팀 전체 할일 · 일정 관리
```
