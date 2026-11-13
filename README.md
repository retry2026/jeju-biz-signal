# 제주 상권 신호등 (jeju-biz-signal)

제주 외식업 창업자와 사장님이 말로 물으면, 동네 손님이 도민인지 관광객인지, 계절과 관광객 흐름에 따라 얼마나 흔들리는지를 공개 데이터로 계산해 답하는 AI 에이전트입니다.

## 요구 사항

| 도구 | 버전 |
|---|---|
| JDK | 21 |
| Node.js | 22.12 이상 (또는 20.19 이상) |

Maven은 따로 설치하지 않아도 됩니다. `backend/mvnw`가 필요한 버전을 받아서 사용합니다.

## 환경변수

| 이름 | 필수 | 설명 |
|---|---|---|
| `LLM_API_KEY` | 예 | LLM API 키 |
| `LLM_MODEL` | 아니오 | 사용할 모델 이름 |
| `DATA_DIR` | 아니오 | 데이터 폴더 경로. 기본값 `../data` (`backend` 폴더 기준) |

`.env.example`에 이름 목록이 있습니다.

## 실행

### 개발 모드

백엔드와 화면을 각각 실행합니다.

```bash
cd backend && ./mvnw spring-boot:run
```

```bash
cd frontend && npm install && npm run dev
```

http://localhost:5173 에서 화면이 열리고, `/api` 요청은 백엔드(8080)로 전달됩니다.

### 빌드 후 실행

```bash
cd frontend && npm install && npm run build
```

```bash
cd backend && ./mvnw package
```

```bash
cd backend && java -jar target/jeju-agent.jar
```

화면 빌드 결과가 jar에 포함되며, http://localhost:8080 에서 열립니다.

## 테스트

```bash
cd backend && ./mvnw test
```

## 기술 스택

| 구분 | 사용 기술 |
|---|---|
| 백엔드 | Java 21, Spring Boot 4.1, Spring AI 2.0 |
| 데이터 | DuckDB (파일 DB) |
| 화면 | React 19, TypeScript, Vite 8, Chart.js |
| 빌드 | Maven, npm |

## 폴더 구조

```
jeju-biz-signal/
├─ backend/     Spring Boot 애플리케이션
├─ frontend/    React 화면
├─ data/        원본 데이터(raw)와 적재 SQL(sql)
├─ eval/        에이전트 평가 질문과 결과
└─ docs/        기획과 설계 문서
```

## 데이터 출처

| 데이터 | 출처 |
|---|---|
| 제주특별자치도 상가(상권) 정보 | 제주데이터허브 |
| 업종별 성별 카드 이용 정보 | 제주데이터허브 (제주빅데이터센터) |
| 제주 지역별 소비 | 제주관광빅데이터플랫폼 (공공누리 제1유형) |
| 입도 관광객 추이(일별) | 제주관광빅데이터플랫폼 (공공누리 제1유형) |
