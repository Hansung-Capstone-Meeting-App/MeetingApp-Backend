# 변경 사항 정리

이 문서는 회의 녹음 분석 기능 개선 작업에서 수정/추가한 내용을 정리한 문서입니다.

## 작업 목적

기존 회의 분석 흐름에서 STT 결과에 오타, 어색한 문장, 잘못된 표현이 섞이는 문제가 있었습니다.

이번 변경의 목표는 다음과 같습니다.

- AssemblyAI 전사 결과를 Gemini로 한 번 더 교정
- 원본 전사와 교정 전사를 모두 저장
- 사용자가 결과보기에서 원본/교정본을 확인 가능하게 처리
- 요청 한 번으로 전사, 교정, 요약, 할 일, 일정 생성까지 완료
- Postman에서 테스트하기 쉽게 최신 API 문서 정리

## 전체 처리 흐름

변경 후 통합 분석 흐름은 다음과 같습니다.

```text
녹음 파일 업로드
→ AssemblyAI 전사
→ Gemini 문장/철자 교정
→ 원본/교정본 transcript 저장
→ Gemini 회의 분석
→ 요약, 키워드, 할 일, 일정 저장
→ 결과 조회
```

## 새로 추가된 통합 API

기존에는 전사 요청과 Gemini 분석 요청을 따로 호출해야 했습니다.

기존 흐름:

```text
POST /api/meetings/{meetingId}/recordings/{recordingId}/transcribe
POST /api/meetings/transcripts/{transcriptId}/gemini-analyze
```

새로 추가한 통합 흐름:

```text
POST /api/meetings/{meetingId}/recordings/{recordingId}/analyze
```

예시:

```text
POST http://localhost:8080/api/meetings/56/recordings/63/analyze
```

이 요청 한 번으로 다음 작업이 모두 실행됩니다.

- STT 전사
- Gemini 교정
- transcript 저장
- Gemini 요약/키워드/할 일/일정 분석
- Task/Event 저장

## Gemini 교정 단계 추가

추가 위치:

```text
src/main/java/com/capston/demo/domain/ai/service/GeminiAiService.java
```

추가된 주요 메서드:

```java
correctTranscript(...)
```

교정 프롬프트의 핵심 기준:

- 철자, 띄어쓰기, 어색한 조사, 문장부호만 자연스럽게 교정
- 회의에서 말하지 않은 새 정보는 추가하지 않음
- 의미가 불확실한 단어, 고유명사, 기술명은 추측해서 바꾸지 않음
- 화자 레이블, 발화 순서, 시작/종료 시간은 유지
- JSON 형식으로만 반환

## 교정 결과 DTO 추가

추가 파일:

```text
src/main/java/com/capston/demo/domain/ai/dto/internal/GeminiCorrectionResult.java
```

역할:

- Gemini가 교정한 전체 전사문 저장
- 화자별 원본 문장과 교정 문장 저장
- 발화 시작/종료 시간 유지

## transcript 저장 구조 변경

수정 파일:

```text
src/main/java/com/capston/demo/domain/meeting/entity/MeetingTranscript.java
```

추가된 필드:

```java
private String originalFullText;
private String correctedFullText;
```

화자별 segment에 추가된 필드:

```java
private String originalContent;
private String correctedContent;
```

의미:

| 필드 | 의미 |
| --- | --- |
| `fullText` | 기본 표시용 전체 전사문, 현재는 교정본 |
| `originalFullText` | AssemblyAI 원본 전체 전사문 |
| `correctedFullText` | Gemini 교정 전체 전사문 |
| `content` | 기본 표시용 문장, 현재는 교정본 |
| `originalContent` | AssemblyAI 원본 문장 |
| `correctedContent` | Gemini 교정 문장 |

## 결과 응답 필드 추가

수정 파일:

```text
src/main/java/com/capston/demo/domain/ai/dto/response/TranscribeResponse.java
src/main/java/com/capston/demo/domain/meeting/dto/response/TranscriptResponse.java
```

추가된 응답 필드:

```json
{
  "fieldDescriptions": {
    "content": "content(표시 문장)",
    "originalContent": "originalContent(원본)",
    "correctedContent": "correctedContent(교정본)",
    "correctionChanged": "correctionChanged(실질 교정 여부)",
    "correctionStatusText": "correctionStatusText(교정 상태)"
  }
}
```

segment 응답 예시:

```json
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
```

## correctionChanged 기준 개선

기존 기준:

```text
원본과 교정본이 한 글자라도 다르면 true
```

문제:

```text
돼요 메신저
돼요. 메신저
```

처럼 마침표 하나만 추가되어도 `true`가 되어 사용자가 보기에는 헷갈릴 수 있었습니다.

변경 후 기준:

```text
공백, 띄어쓰기, 마침표, 쉼표, 물음표 같은 문장부호만 다르면 false
단어 또는 글자 자체가 바뀌면 true
```

예시:

```text
돼요 메신저
돼요. 메신저
```

결과:

```json
"correctionChanged": false,
"correctionStatusText": "원문 유지"
```

예시:

```text
제이나이 API
Gemini API
```

결과:

```json
"correctionChanged": true,
"correctionStatusText": "교정됨"
```

## 교정 실패 fallback 추가

수정 파일:

```text
src/main/java/com/capston/demo/domain/ai/service/MeetingAnalysisService.java
```

Gemini 교정 API가 실패하면 전체 분석이 중단되지 않도록 처리했습니다.

fallback 동작:

```text
Gemini 교정 실패
→ AssemblyAI 원본을 교정본처럼 사용
→ originalContent와 correctedContent를 동일하게 저장
→ correctionChanged는 false
```

## 통합 분석 응답 추가

추가 파일:

```text
src/main/java/com/capston/demo/domain/ai/dto/response/MeetingAnalyzeResponse.java
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
  "originalFullText": "원본 전체 전사문",
  "correctedFullText": "교정된 전체 전사문",
  "summary": "회의 요약",
  "keywords": ["키워드"],
  "savedTaskCount": 2,
  "savedEventCount": 1
}
```

## Postman 문서 갱신

수정 파일:

```text
POSTMAN_API_GUIDE.md
```

기존 문서는 예전 URL 기준이었습니다.

예전 문서:

```text
POST /user
POST /login
POST /api/meetings/{meetingId}/analyze
```

현재 코드 기준으로 갱신:

```text
POST /api/user/register
POST /api/auth/login
POST /api/meetings/{meetingId}/recordings/{recordingId}/analyze
```

문서에 포함된 내용:

- 서버 실행 방법
- 회원가입/로그인
- Bearer Token 넣는 법
- 워크스페이스 생성
- 회의 생성
- 녹음 파일 업로드
- 통합 분석 요청
- 분석 결과 조회
- 일정 조회
- 할 일 조회
- 자주 나는 오류와 해결법

## 주요 변경 파일

```text
POSTMAN_API_GUIDE.md
src/main/java/com/capston/demo/domain/ai/controller/MeetingAnalysisController.java
src/main/java/com/capston/demo/domain/ai/controllerDocs/MeetingAnalysisControllerDocs.java
src/main/java/com/capston/demo/domain/ai/dto/internal/GeminiCorrectionResult.java
src/main/java/com/capston/demo/domain/ai/dto/response/MeetingAnalyzeResponse.java
src/main/java/com/capston/demo/domain/ai/dto/response/TranscribeResponse.java
src/main/java/com/capston/demo/domain/ai/service/GeminiAiService.java
src/main/java/com/capston/demo/domain/ai/service/MeetingAnalysisService.java
src/main/java/com/capston/demo/domain/meeting/dto/response/TranscriptResponse.java
src/main/java/com/capston/demo/domain/meeting/entity/MeetingTranscript.java
src/main/java/com/capston/demo/domain/meeting/service/MeetingTranscriptService.java
```

## 테스트 방법

컴파일 확인:

```powershell
.\gradlew.bat compileJava
```

확인 결과:

```text
BUILD SUCCESSFUL
```

서버 실행:

```powershell
.\gradlew.bat bootRun
```

서버가 이미 켜져 있는 상태에서 코드를 수정했다면 반드시 재시작해야 합니다.

## Postman 테스트 순서

자세한 사용법은 아래 문서를 참고합니다.

```text
POSTMAN_API_GUIDE.md
```

요약:

```text
1. 회원가입
2. 로그인 후 accessToken 복사
3. 워크스페이스 생성
4. 회의 생성
5. 녹음 파일 업로드
6. 통합 분석 요청
7. 결과보기
8. 일정조회
9. 할일조회
```
