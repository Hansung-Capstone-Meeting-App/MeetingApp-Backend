# MeetingApp Backend Postman 사용 가이드

이 문서는 현재 코드 기준의 Postman 테스트 순서입니다.

서버 주소:

```text
http://localhost:8080
```

## 0. 서버 실행 확인

서버 실행:

```powershell
cd C:\Users\wookj\OneDrive\문서\GitHub\restart\MeetingApp-Backend
.\gradlew.bat bootRun
```

서버가 이미 켜져 있는 상태에서 코드를 수정했다면 반드시 재시작해야 합니다.

Swagger:

```text
http://localhost:8080/swagger-ui/index.html
```

## 1. 회원가입

Authorization은 넣지 않습니다.

```text
POST http://localhost:8080/api/user/register
```

Body: `raw` + `JSON`

```json
{
  "email": "test1234@test.com",
  "password": "test1234!",
  "displayName": "문자교정모",
  "profileImageUrl": "https://picsum.photos/200"
}
```

성공 기준:

```text
201 Created
```

응답 예시:

```json
{
  "id": 24,
  "email": "test1234@test.com",
  "name": "문자교정모",
  "createdAt": "2026-05-12T02:01:05.1346709"
}
```

## 2. 로그인

Authorization은 넣지 않습니다.

```text
POST http://localhost:8080/api/auth/login
```

Body: `raw` + `JSON`

```json
{
  "email": "test1234@test.com",
  "password": "test1234!"
}
```

성공하면 `accessToken`을 복사합니다.

```json
{
  "accessToken": "eyJ...",
  "refreshToken": "eyJ...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "userId": 24,
  "name": "문자교정모"
}
```

## 3. 토큰 넣는 방법

이후 대부분의 요청은 Authorization이 필요합니다.

Postman 설정:

```text
Authorization 탭
Type: Bearer Token
Token: accessToken 값만 붙여넣기
```

주의:

```text
Bearer 글자는 직접 붙이지 않습니다.
```

## 4. 워크스페이스 생성

```text
POST http://localhost:8080/api/workspaces
```

Authorization: `Bearer Token`

Body: `raw` + `JSON`

```json
{
  "name": "테스트 워크스페이스"
}
```

응답의 `id`를 복사합니다. 이 값이 `workspaceId`입니다.

응답 예시:

```json
{
  "id": 12,
  "name": "테스트 워크스페이스",
  "slug": "abc123",
  "ownerId": 24,
  "ownerName": "문자교정모",
  "createdAt": "2026-05-12T02:17:11.6719174"
}
```

## 5. 회의 생성

`workspaceId`는 4번에서 받은 값으로 바꿉니다.

```text
POST http://localhost:8080/api/meetings
```

Authorization: `Bearer Token`

Body: `raw` + `JSON`

```json
{
  "workspaceId": 12,
  "title": "문자 교정 테스트 회의"
}
```

응답의 `id`를 복사합니다. 이 값이 `meetingId`입니다.

응답 예시:

```json
{
  "id": 56,
  "workspaceId": 12,
  "title": "문자 교정 테스트 회의",
  "createdBy": 24,
  "createdAt": "2026-05-12T02:19:19.4851682"
}
```

## 6. 녹음 파일 업로드

`meetingId`는 5번에서 받은 값으로 바꿉니다.

```text
POST http://localhost:8080/api/recordings/upload?meetingId=56
```

Authorization: `Bearer Token`

Body: `form-data`

| Key | Type | Value |
| --- | --- | --- |
| `file` | `File` | 업로드할 `.m4a`, `.mp3`, `.wav` 파일 |

주의:

```text
raw JSON이 아닙니다.
Content-Type: application/json을 직접 넣지 않습니다.
Postman이 multipart/form-data로 자동 설정하게 둡니다.
```

응답의 `recordingId`를 복사합니다.

응답 예시:

```json
{
  "recordingId": 63,
  "meetingId": 56,
  "s3Bucket": "hansung-capstone-2026",
  "s3Key": "recordings/56/example.m4a",
  "fileSize": 2106196,
  "durationSec": null,
  "status": "UPLOADED",
  "createdAt": "2026-05-12T02:23:26.9997007"
}
```

## 7. 통합 분석 요청

이 API 하나로 전사, 문장 교정, 요약, 할 일, 일정 저장까지 처리합니다.

`meetingId`와 `recordingId`는 앞 단계에서 받은 값으로 바꿉니다.

```text
POST http://localhost:8080/api/meetings/56/recordings/63/analyze
```

Authorization: `Bearer Token`

Body: `none`

처리 순서:

```text
AssemblyAI 전사
→ Gemini 문장/철자 교정
→ 원본/교정본 transcript 저장
→ Gemini 요약/키워드/할 일/일정 분석
→ tasks/events 저장
```

응답 예시:

```json
{
  "fieldDescriptions": {
    "originalFullText": "originalFullText(원본 전체 전사)",
    "correctedFullText": "correctedFullText(교정된 전체 전사)",
    "summary": "summary(회의 요약)",
    "keywords": "keywords(핵심 키워드)",
    "savedTaskCount": "savedTaskCount(저장된 할 일 수)",
    "savedEventCount": "savedEventCount(저장된 일정 수)"
  },
  "transcriptId": "665f...",
  "originalFullText": "AssemblyAI 원본 전사문",
  "correctedFullText": "Gemini 교정 전사문",
  "summary": "회의 요약",
  "keywords": ["키워드"],
  "savedTaskCount": 2,
  "savedEventCount": 1
}
```

## 8. 분석 결과 조회

`meetingId`는 5번에서 받은 값으로 바꿉니다.

```text
GET http://localhost:8080/api/meetings/56/transcript
```

Authorization: `Bearer Token`

Body: `none`

응답에서 교정 관련 필드는 다음 의미입니다.

| 필드 | 의미 |
| --- | --- |
| `content` | 화면에 기본으로 보여줄 문장 |
| `originalContent` | AssemblyAI 원본 전사 |
| `correctedContent` | Gemini 교정 전사 |
| `correctionChanged` | 실질 교정 여부 |
| `correctionStatusText` | 한글 교정 상태 |

응답 예시:

```json
{
  "fieldDescriptions": {
    "content": "content(표시 문장)",
    "originalContent": "originalContent(원본)",
    "correctedContent": "correctedContent(교정본)",
    "correctionChanged": "correctionChanged(실질 교정 여부)",
    "correctionStatusText": "correctionStatusText(교정 상태)"
  },
  "id": "665f...",
  "meetingId": 56,
  "recordingId": 63,
  "fullText": "교정된 전체 전사문",
  "originalFullText": "원본 전체 전사문",
  "correctedFullText": "교정된 전체 전사문",
  "segments": [
    {
      "speakerLabel": "A",
      "speakerName": null,
      "userId": null,
      "content": "교정된 문장",
      "originalContent": "원본 문장",
      "correctedContent": "교정된 문장",
      "correctionChanged": true,
      "correctionStatusText": "교정됨",
      "startSec": 2.478,
      "endSec": 6.938,
      "sequence": 0
    }
  ]
}
```

`correctionChanged` 기준:

```text
공백, 띄어쓰기, 마침표, 쉼표, 물음표 같은 문장부호만 다르면 false
단어 또는 글자 자체가 바뀌면 true
```

## 9. 일정 조회

통합 분석 요청 이후 조회합니다.

```text
GET http://localhost:8080/api/events?meetingId=56
```

Authorization: `Bearer Token`

Body: `none`

## 10. 할 일 조회

통합 분석 요청 이후 조회합니다.

```text
GET http://localhost:8080/api/tasks?meetingId=56
```

Authorization: `Bearer Token`

Body: `none`

## 전체 순서 요약

```text
1. POST /api/user/register
2. POST /api/auth/login
3. accessToken 복사
4. POST /api/workspaces
5. POST /api/meetings
6. POST /api/recordings/upload?meetingId={meetingId}
7. POST /api/meetings/{meetingId}/recordings/{recordingId}/analyze
8. GET  /api/meetings/{meetingId}/transcript
9. GET  /api/events?meetingId={meetingId}
10. GET /api/tasks?meetingId={meetingId}
```

## 자주 나는 오류

### 403 Forbidden

원인:

```text
Authorization 토큰이 없거나, 다른 계정 토큰이거나, 해당 워크스페이스/회의 권한이 없음
```

해결:

```text
로그인 다시 하기
새 accessToken 복사
Authorization 탭에서 Bearer Token에 붙여넣기
workspaceId는 내가 만든 워크스페이스 id 사용
meetingId는 내가 만든 회의 id 사용
```

### 워크스페이스 멤버가 아닙니다

원인:

```text
회의 생성 Body의 workspaceId가 내 워크스페이스가 아님
```

해결:

```text
GET /api/workspaces 로 내가 속한 워크스페이스 id 확인
또는 POST /api/workspaces 로 새 워크스페이스 생성 후 그 id 사용
```

### 일정/할 일이 빈 배열로 나옴

원인:

```text
통합 분석 요청을 아직 하지 않았거나, 회의 내용에서 추출할 일정/할 일이 없음
```

해결:

```text
POST /api/meetings/{meetingId}/recordings/{recordingId}/analyze 먼저 실행
그 후 GET /api/events, GET /api/tasks 조회
```
