# AGENTS.md

이 저장소에서 일하는 모든 AI 코딩 도구(Claude Code, Cursor, Codex, Copilot 등)가 **작업 전에 반드시 읽는 규칙**이다. 사람도 같은 규칙을 따른다.

## 프로젝트 한 줄

제주 외식업 창업자와 사장님이 말로 물으면, 동네 손님이 도민인지 관광객인지, 계절과 관광객 흐름에 따라 얼마나 흔들리는지를 공개 데이터로 계산해 답하는 AI 에이전트. 공모전 제출 마감 2026-11-20 10:00.

## 먼저 읽을 문서

| 문서 | 내용 | 언제 |
|---|---|---|
| `docs/IDEA.md` | 왜 만드는지 한 장 요약 | 처음 한 번 |
| `docs/PRD.md` | 무엇을 만드는지 (기능 요구사항 FR-xx) | 기능을 만들기 전 |
| `docs/TRD.md` | 어떻게 만드는지 (구조, 기술, 제약) | 코드를 쓰기 전 |
| `docs/API명세.md` | 화면 API, 뷰, record, LLM 출력의 정확한 모양 | **계약 코드를 만들거나 쓸 때 항상** |
| `docs/TDD.md` | 테스트를 어떻게 먼저 쓰고 확인하는지 | 코드를 쓸 때 항상 |

문서와 코드가 다르면 **문서가 맞다**. 문서가 틀렸다고 생각되면 코드를 바꾸지 말고 사람에게 알린다.

## 기술 버전 (바꾸지 않는다)

| 항목 | 버전 |
|---|---|
| Java | 21 |
| Spring Boot | 4.1.x |
| Spring AI | 2.0.x |
| 빌드 | Maven (`./mvnw`) |
| DB | DuckDB JDBC 2.0.x (파일 DB, 서버 없음) |
| DB 접근 | Spring `JdbcClient` (JPA 쓰지 않음) |
| 화면 | React 19 + TypeScript + Vite 8 |
| 차트 | Chart.js 4 + react-chartjs-2 5 |

- 새 의존성을 추가하지 않는다. 꼭 필요하면 사람에게 먼저 묻는다.
- 버전을 올리거나 내리지 않는다.

## 자주 틀리는 것 (반드시 지킨다)

1. **Jackson 3:** Spring Boot 4는 Jackson 3을 쓴다. import는 `tools.jackson.*`이다. `com.fasterxml.jackson.databind.*`를 쓰지 않는다. (애너테이션 `com.fasterxml.jackson.annotation.*`은 그대로)
2. **Spring AI 2.0:** 예전 버전(0.8, 1.0) 예제의 API 이름이 다를 수 있다. `ChatClient` 빌더, `@Tool`, `.entity(Class)`를 쓴다. 확실하지 않으면 공식 문서를 확인한다.
3. **SQL 예약어:** `date`, `foreign`, `user`, `order`를 컬럼 이름으로 쓰지 않는다.
4. **비율은 퍼센트 숫자, 금액은 원 단위 정수.** 0~1 비율을 쓰지 않는다.
5. **없는 값은 `null`.** 0이나 빈 문자열로 대신하지 않는다.

## 폴더 소유권 (자기 폴더만 고친다)

| 역할 | 고칠 수 있는 곳 |
|---|---|
| ① 데이터 | `data/`, `backend/src/main/java/kr/jeju/agent/config/`, `eval/questions.yaml` |
| ② 지표 | `.../metric/`, `.../chart/`, `backend/src/test/.../metric/`, `eval/gold/`, `docs/metric-spec.md` |
| ③ 에이전트 | `.../agent/`, `.../eval/`, `backend/src/main/resources/prompts/`, `eval/results/` |
| ④ 화면과 연동 | `frontend/`, `.../web/` |
| 공동 (PR에 관련 역할 승인 필요) | `docs/API명세.md`, `metric/*Result.java`, `data/sql/04_views.sql`, `web/dto/*`, `frontend/src/types.ts`, `agent/ChatAgent.java`, `pom.xml`, `AGENTS.md` |

작업 중 다른 역할의 파일을 고쳐야 한다면 **멈추고** 무엇을 왜 바꿔야 하는지 사람에게 알린다.

## 절대 하지 않는 것

- API 키, 비밀번호, 토큰을 코드, 설정 파일, 문서, 커밋에 넣지 않는다. 키는 환경변수(`LLM_API_KEY`)로만 읽는다.
- LLM이 숫자를 만들게 하지 않는다. 숫자는 `metric`이 계산하고, 차트는 `chart`가 만든다. LLM 출력(`AgentReply`)에는 숫자 필드가 없다.
- 데이터에 없는 질문에 답을 지어내게 하지 않는다. 거절하고 대안을 제시한다.
- 소상공인365 등 외부 웹사이트 화면 데이터를 긁어오는 코드를 만들지 않는다.
- `data/raw/`의 원본 CSV를 수정하지 않는다. 정제는 `data/sql/`에서 한다.
- 테스트를 지우거나 `@Disabled`로 꺼서 통과시키지 않는다.
- `main` 브랜치에 직접 push하지 않는다.

## 작업 방식

1. 작업 브랜치: `feat/역할-내용` (예: `feat/metric-area`).
2. **테스트를 먼저 쓴다** (`docs/TDD.md`). 테스트가 실패하는 것을 본 뒤 구현한다.
3. 끝나면 확인한다.
   - 백엔드: `cd backend && ./mvnw test`
   - 화면: `cd frontend && npm run build`
   - 실행: `./mvnw spring-boot:run` 후 해당 기능을 직접 호출
4. PR은 작게. 제목에 접두어 `[data]`, `[metric]`, `[agent]`, `[web]`.
5. 사람에게 넘길 때 무엇을 바꿨는지, 어떻게 확인했는지, 확인하지 못한 것이 무엇인지 적는다.

## 코드 스타일

- 패키지 루트: `kr.jeju.agent`
- DTO와 도구 결과는 Java `record`.
- SQL은 Java 문자열 안에 텍스트 블록(`"""`)으로 쓰고, 파라미터는 반드시 바인딩한다 (문자열 이어붙이기 금지).
- 프롬프트는 코드에 넣지 않고 `resources/prompts/*.md` 파일에서 읽는다.
- 주석과 커밋 메시지는 한국어.
- 화면 컴포넌트는 함수형, 상태는 `useState`로 충분하다. 상태관리 라이브러리를 추가하지 않는다.
