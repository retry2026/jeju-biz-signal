# TRD — 기술 요구사항

버전 1.0 · 2026-11-13 · 기준 문서: `docs/PRD.md` · 인터페이스 상세: `docs/API명세.md`

## 1. 구조

```
[브라우저: React 화면]
      │  GET /api/meta, POST /api/chat
      ▼
[Spring Boot 앱 하나 (java -jar)]
  web/      ChatController, MetaController, SessionStore
      │  ChatAgent.answer(ChatRequest)
      ▼
  agent/    InputGuard → AgentService(ChatClient) → 응답 조립
      │        │  @Tool 호출               ▲ chartIds
      │        ▼                           │
  metric/   CompetitionMetric, AreaCustomerMetric, IndustryTourismMetric
  chart/    ChartAssembler (도구 결과 → Chart.js JSON)
      │  JdbcClient SQL
      ▼
  config/   DuckDbConfig, DataLoader
      │  시작 시 data/sql/*.sql 실행
      ▼
[data/jeju.duckdb]  ← data/raw/*.csv (S1~S4)
      
[외부] LLM API (도구 호출 지원)  ← agent만 호출
```

### 요청 하나의 흐름

1. `ChatController`가 요청을 검증하고 `ChatAgent.answer()`를 부른다.
2. `InputGuard`가 지역, 업종, 질문을 검사한다. 비었으면 LLM 없이 `ASK_BACK`.
3. `AgentService`가 `ChatClient`로 LLM을 부른다. system 프롬프트, 세션 대화 기록, 도구 3개를 넘긴다.
4. LLM이 도구를 고른다. 도구는 `metric`을 실행하고 결과 record를 돌려주며, 같은 결과를 `EvidenceCollector`에 저장한다.
5. LLM이 `AgentReply`(type, summary, sections, chartIds, suggestions)를 구조화 출력으로 돌려준다. 숫자 필드는 없다.
6. `ChartAssembler`가 `chartIds`와 `EvidenceCollector`의 결과로 차트를 만들고, `LimitBuilder`가 한계 문구를, `EvidenceCollector`가 근거 목록을 만든다.
7. `ChatResponse`를 돌려준다.

## 2. 기술 스택

| 구분 | 선택 | 버전 | 이유 |
|---|---|---|---|
| 언어 | Java (Temurin) | 21 | LTS, Spring Boot 4 지원 |
| 프레임워크 | Spring Boot | 4.1.x | 최신 안정판 |
| LLM 연동 | Spring AI | 2.0.x | `ChatClient`, `@Tool`, 대화 메모리, 구조화 출력. 공급자를 설정으로 교체 가능 |
| 빌드 | Maven | Wrapper | 팀 결정 |
| DB | DuckDB (JDBC) | 2.0.x | 서버 없는 파일 DB. CSV를 SQL로 바로 읽음. 집계가 빠름 |
| DB 접근 | Spring `JdbcClient` | Boot 포함 | 지표가 SQL 중심이라 ORM이 필요 없음 |
| 화면 | React + TypeScript | 19 | 응답 타입을 화면 코드에서 강제 |
| 화면 빌드 | Vite | 8 | 빌드 결과를 Spring 정적 파일로 |
| 차트 | Chart.js + react-chartjs-2 | 4 / 5 | 응답의 차트 JSON을 그대로 렌더링 |
| LLM | 도구 호출 지원 외부 API | 미정 (11/13 결정) | |

### DuckDB를 쓰는 이유 (MySQL과 비교)

| 기준 | DuckDB | MySQL |
|---|---|---|
| 설치 | 없음. jar 안의 라이브러리 | 서버 설치, 계정, 실행 필요 |
| 센터 PC, 심사위원 재현 | `java -jar` 한 줄 | MySQL 설치 절차가 README에 추가되고 실패 지점이 늘어남 |
| CSV 적재 | `read_csv()`로 SQL 한 줄 | `LOAD DATA` 권한, 인코딩, 테이블 정의 필요 |
| 우리 사용 패턴 (읽기 전용 집계, 15MB) | 맞음 | 과함 (쓰기, 동시성, 트랜잭션이 필요 없음) |
| SQL 문법 | 표준 SQL에 가까움 (PostgreSQL 계열) | 익숙함 |

## 3. 모듈별 요구사항

### config

- `DuckDbConfig`: `${DATA_DIR}/jeju.duckdb`로 `DataSource`를 만든다. 기본 `DATA_DIR`은 `../data`.
- `DataLoader`: 앱 시작 시 `jeju.duckdb`가 없거나 `data/sql/*.sql`이 더 최신이면, SQL을 파일 이름 순서로 실행해 다시 만든다.
- SQL 4단계: `01_raw`(CSV → 원본 테이블), `02_mapping`(업종·지역 대응표), `03_clean`(걸러내기, `filter_log`), `04_views`(뷰 4개).

### metric

- 서비스 3개, 메서드는 `API명세.md` 5장 시그니처 그대로.
- 뷰만 조회한다. 원본 테이블을 직접 읽지 않는다.
- 비율은 퍼센트, 금액은 원 정수, 없는 값은 `null`.
- 시점이 다른 데이터를 나눌 때는 같은 기준 월로 맞춘다 (예: 점포당 결제 건수는 2025-09).
- `MetricMeta`에 출처, 기간, 기준(AREA/RADIUS/JEJU), 표본 부족 여부를 채운다.
- 반경 거리는 하버사인 공식(SQL)으로 계산한다. 공간 확장은 쓰지 않는다.

### chart

- `ChartAssembler.build(List<String> chartIds, EvidenceSnapshot results)` → `List<Chart>`.
- 차트 7종 (`API명세.md` 3장). 필요한 도구 결과가 없는 차트 ID는 건너뛴다.
- 숫자를 가공하지 않는다 (반올림만 소수 첫째 자리).

### agent

- `ChatAgent` 인터페이스: `ChatResponse answer(ChatRequest request)`.
- `AgentTools`: 도구 3개. `@Tool` 설명에 "언제 쓰는지, 무엇을 돌려주는지, 기준이 동네인지 제주 전체인지"를 쓴다.
- `EvidenceCollector`: 요청 범위. 도구 결과와 호출 순서를 저장.
- 대화 메모리: 세션 ID별 최근 10개 메시지. 메모리 저장소는 인메모리.
- LLM 호출 타임아웃 50초. 도구 호출은 한 요청에 최대 6번.
- 프롬프트: `prompts/system.md`(역할, 원칙, 도구 사용 규칙, 답변 구조), `prompts/refusal.md`(거절 목록과 대안).
- LLM 오류 시 `LLM_UNAVAILABLE` 예외를 던진다.

### web

- `POST /api/chat`, `GET /api/meta` (`API명세.md` 2~3장).
- 요청 검증: `jakarta.validation`. 코드값은 `ref_area`, `ref_industry`에 있는지 확인.
- `SessionStore`: 세션 ID별 마지막 사용 시각. 30분 지나면 대화 메모리와 함께 정리 (`@Scheduled`).
- `ApiExceptionHandler`: 400 `INVALID_REQUEST`, 503 `LLM_UNAVAILABLE`, 500 `INTERNAL_ERROR`.
- `FakeChatAgent`: `mocks/*.json`을 돌려주는 개발용 구현. 프로필 `fake`에서만 활성.

### frontend

- 화면 5개, 결과 섹션 순서는 PRD FR-41.
- `api.ts`에서만 서버를 부른다. 타임아웃 60초.
- 차트는 응답의 `type`, `labels`, `datasets`를 그대로 Chart.js에 넘긴다. 화면에서 숫자를 계산하지 않는다.
- `useIdleReset`: 2분 무조작이면 처음 화면, 새 `sessionId`.
- 빌드 결과는 `backend/src/main/resources/static`.

### eval

- `EvalRunner` (Java main): `eval/questions.yaml`을 읽어 실행 중인 앱의 `/api/chat`을 질문마다 N번(기본 3) 호출.
- 채점: 응답 `type`이 기대와 같은지, 기대 도구가 `evidence`에 있는지, `eval/gold/*.sql`을 DuckDB에 실행한 값이 본문 숫자와 허용 오차 안인지.
- 결과: `eval/results/YYYY-MM-DD-HHmm.md` (맨 위에 커밋 해시, 모델 이름).

## 4. 설정

```yaml
# application.yml (예시, 실제 키 이름은 공급자 starter에 맞춘다)
app:
  data-dir: ${DATA_DIR:../data}
  llm:
    timeout-seconds: 50
    max-tool-calls: 6
  session:
    ttl-minutes: 30
spring:
  ai:
    # 공급자별 설정. 키는 반드시 ${LLM_API_KEY}, 모델은 ${LLM_MODEL:기본값}
```

| 환경변수 | 필수 | 설명 |
|---|---|---|
| `LLM_API_KEY` | 예 | LLM API 키 |
| `LLM_MODEL` | 아니오 | 모델 이름 |
| `DATA_DIR` | 아니오 | 데이터 폴더 |

## 5. 비기능 요구사항

| 항목 | 요구사항 |
|---|---|
| 실행 | JDK 21만 있으면 `java -jar` 한 줄 |
| 성능 | 지표 SQL 하나 1초 이내. 전체 응답 대부분 30초 이내 (LLM 포함) |
| 메모리 | 힙 1GB 이내 |
| 보안 | 키는 환경변수. 로그에 키와 전체 프롬프트를 남기지 않음. SQL은 파라미터 바인딩 |
| 재현성 | 원본 CSV와 SQL로 DuckDB 파일을 다시 만들 수 있음. 같은 입력이면 같은 숫자 |
| 로그 | 요청마다 세션 ID, 호출된 도구, 소요 시간, 응답 유형 한 줄 |

## 6. 실행과 빌드

```bash
# 개발
cd backend && ./mvnw spring-boot:run
cd frontend && npm run dev          # http://localhost:5173, /api는 8080으로 프록시

# 가짜 에이전트로 화면만 개발
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=fake

# 제출
cd frontend && npm run build        # backend/src/main/resources/static 으로 출력
cd backend && ./mvnw package
java -jar backend/target/jeju-agent.jar
```

## 7. 제약과 위험

| 위험 | 대응 |
|---|---|
| S3 값이 너무 작아 쓸 수 없음 | 동네 손님 구성을 빼고 S2 + S1로 축소 (11/13 판단) |
| LLM 도구 호출이 불안정 | 분류와 거절을 코드 분기로 고정, LLM은 도구 선택과 문장만 |
| Spring AI 2.0 API를 AI 도구가 잘못 씀 | 공식 문서 예제를 함께 제공, 버전 명시 |
| 센터 PC 인터넷 불가 | 기획서에 외부 LLM API 필요 명시 |
| 응답이 느림 | 도구 호출 최대 6번, 타임아웃 50초 |
