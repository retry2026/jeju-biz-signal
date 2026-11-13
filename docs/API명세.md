# API 명세서

버전 0.1 (초안) · 작성 2026-11-13 · **11/13 회의에서 확정 후 0.2로 올린다**

저장소에서는 `docs/API명세.md`에 두고, 바꿀 때마다 맨 아래 변경 이력에 적는다.

---

## 1. 이 문서를 쓰는 방법

### 무엇을 정하는 문서인가

이 프로젝트에는 사람 사이의 경계가 세 군데 있다. 경계마다 모양을 정해 두면 각자 가짜 데이터로 따로 개발할 수 있다.

| 장 | 경계 | 만드는 쪽 → 쓰는 쪽 | 코드에서의 모양 |
|---|---|---|---|
| 2~3장 | 화면 API | ③④ 백엔드 → ④ 화면 | `web/dto/*.java`, `frontend/src/types.ts` |
| 4장 | 데이터 뷰 | ① → ② | `data/sql/04_views.sql` |
| 5장 | 도구 결과 | ② → ③ | `metric/*Result.java` |
| 6장 | LLM 출력 | LLM → ③ | `agent/AgentReply.java` |
| 7장 | 평가 질문 | ①② → ③ | `eval/questions.yaml` |

### 작성 원칙

1. **이 문서가 기준이다.** 코드(DTO, 타입, 뷰, record)는 이 문서를 따라 만든다. AI 도구에게도 이 문서를 주고 코드를 만들게 한다.
2. **필드마다 타입, 필수 여부, 단위, 예시를 적는다.** 단위가 애매한 숫자(%, 비율, 원, 만원)가 가장 많이 틀린다.
3. **예시 JSON을 같이 둔다.** `docs/api-examples/*.json`의 예시를 ④는 화면 가짜 응답으로, ③은 응답 조립 테스트로 그대로 쓴다.
4. **바꾸려면 먼저 알리고, 같은 PR에서 문서와 코드를 함께 고친다.** 쓰는 쪽 담당이 승인해야 합친다.
5. **Swagger 같은 자동 생성 도구는 쓰지 않는다.** 엔드포인트가 2개뿐이라 문서와 예시 JSON으로 충분하다.

### 공통 규칙

| 항목 | 규칙 |
|---|---|
| 기본 주소 | 개발 `http://localhost:8080`, 화면 개발 서버는 `/api`를 프록시 |
| 형식 | JSON, UTF-8 |
| 필드 이름 | camelCase |
| 날짜 | 월은 `"YYYY-MM"`, 날짜는 `"YYYY-MM-DD"` 문자열 |
| 비율 | **퍼센트 숫자** (예: 31.2는 31.2%). 0~1 비율은 쓰지 않는다 |
| 금액 | **원 단위 정수** |
| 배수 | 소수 (예: 1.8은 1.8배) |
| 없는 값 | `null` (빈 문자열이나 0으로 대신하지 않는다) |
| 인증 | 없음 (센터 키오스크 내부 사용) |

---

## 2. GET /api/meta

화면이 시작할 때 한 번 부른다. 드롭다운, 버튼, 예시 질문, 데이터 기준일을 받는다.

### 응답 200

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `areas` | `Area[]` | O | 읍면동 43개 |
| `areas[].code` | string | O | 행정동 코드 8자리 (S1 기준) |
| `areas[].name` | string | O | 화면 표시 이름 |
| `areas[].city` | string | O | `제주시` 또는 `서귀포시` (드롭다운 묶음용) |
| `industries` | `Industry[]` | O | 외식업 7개 |
| `industries[].code` | string | O | `CAFE`, `KOREAN`, `CHINESE`, `JAPANESE`, `WESTERN`, `PUB`, `FASTFOOD` |
| `industries[].name` | string | O | 화면 표시 이름 |
| `exampleQuestions` | object | O | 사용자 유형별 예시 질문 |
| `exampleQuestions.STARTUP` | string[] | O | 3~4개 |
| `exampleQuestions.EXISTING` | string[] | O | 3~4개 |
| `dataAsOf` | object | O | 데이터 기준일 (화면 하단 표시용) |
| `dataAsOf.store` | string | O | S1 기준일 |
| `dataAsOf.areaSpend` | string | O | S3 마지막 월 |
| `dataAsOf.industrySpend` | string | O | S2 마지막 월 |
| `dataAsOf.arrivals` | string | O | S4 마지막 날 |

### 예시

```json
{
  "areas": [
    { "code": "50110660", "name": "노형동", "city": "제주시" },
    { "code": "50110250", "name": "한림읍", "city": "제주시" }
  ],
  "industries": [
    { "code": "CAFE", "name": "카페" },
    { "code": "KOREAN", "name": "한식 (고깃집 포함)" }
  ],
  "exampleQuestions": {
    "STARTUP": ["이 동네에 카페 차려도 괜찮을까요?", "손님은 도민이 많나요, 관광객이 많나요?"],
    "EXISTING": ["겨울만 되면 매출이 줄어요. 정상인가요?", "작년보다 매출이 줄었는데 우리 가게 문제인가요?"]
  },
  "dataAsOf": { "store": "2025-09-18", "areaSpend": "2026-09", "industrySpend": "2025-06", "arrivals": "2026-11-12" }
}
```

---

## 3. POST /api/chat

질문 하나를 보내고 답 하나를 받는다. 같은 `sessionId`로 보내면 앞 대화를 기억한다.

### 요청

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `sessionId` | string | O | 화면이 만든 UUID. 리셋하면 새로 만든다 |
| `userType` | string | O | `STARTUP`(예비 창업자) 또는 `EXISTING`(기존 사업자) |
| `area` | string | O | `areas[].code` |
| `industry` | string | O | `industries[].code` |
| `myStore` | object | X | 기존 사업자 매출 입력. 없으면 `null` |
| `myStore.monthlySales` | `{month, amount}[]` | X | 1~12개. `month`는 `"YYYY-MM"`, `amount`는 원 |
| `message` | string | O | 질문. 1~500자 |

```json
{
  "sessionId": "8f1c2a4e-1b7d-4c55-9a0e-2d3f4b5c6d7e",
  "userType": "EXISTING",
  "area": "50110250",
  "industry": "CAFE",
  "myStore": null,
  "message": "애월에서 카페 하는데 겨울만 되면 매출이 반토막 나요. 정상인가요?"
}
```

### 응답 200

| 필드 | 타입 | 필수 | 설명 | 누가 만드나 |
|---|---|---|---|---|
| `type` | string | O | `ANSWER`, `REFUSE`, `ASK_BACK` | LLM (빈 입력이면 코드) |
| `summary` | string | O | 한두 문장 결론 | LLM |
| `sections` | `Section[]` | O | 본문. `ASK_BACK`이면 빈 배열 | LLM |
| `charts` | `Chart[]` | O | 0~3개 | 코드 (② `ChartAssembler`) |
| `evidence` | `Evidence[]` | O | 실제로 호출된 도구와 출처 | 코드 |
| `limits` | string[] | O | 한계 문구 | 코드 |
| `suggestions` | string[] | O | 다음 질문 제안 0~3개 (화면에서 버튼) | LLM |
| `askFor` | string | X | `ASK_BACK`일 때 무엇이 필요한지: `AREA`, `INDUSTRY`, `MESSAGE`, `MY_STORE` | 코드 또는 LLM |

**Section**

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | string | `competition`(경쟁), `customers`(동네 손님), `industry`(업종 성격), `tourism`(관광 흐름), `myStore`(내 가게 비교), `advice`(고려해 볼 점) |
| `title` | string | 화면 제목 |
| `text` | string | 본문. 숫자는 도구 결과를 그대로 옮긴 것만 |

**Chart** (Chart.js에 바로 넘기는 모양)

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | string | 차트 ID (아래 표) |
| `type` | string | `doughnut`, `bar`, `line` |
| `title` | string | 차트 제목 |
| `labels` | string[] | x축 또는 조각 이름 |
| `datasets` | `{label, data, dashed?}[]` | `data`는 number 배열, `null` 허용(표본 부족). `dashed`가 true면 점선 (평균선) |
| `unit` | string | `%`, `원`, `건`, `개`, `명`, `지수` |
| `source` | string | 출처와 기간 (차트 아래 표시) |
| `scope` | string | `AREA`, `RADIUS`, `JEJU` |

차트 ID

| id | type | 내용 | 데이터 |
|---|---|---|---|
| `customerMix` | doughnut | 동네 외식 결제 도민·내국인 관광객·외국인 관광객 비중 (%) | S3 |
| `areaMonthly` | line | 동네 외식 결제 월별 금액, 도민과 관광객 선, 제주 동 평균 점선 | S3 |
| `ticketSize` | bar | 도민과 관광객 건당 결제액 (원) | S3 |
| `touristFlow` | line | 최근 4주 일별 입도객, 올해와 작년 | S4 |
| `industrySeason` | line | 업종 월별 계절 지수 (연평균 = 100) | S2 |
| `competition` | bar | 동네 점포 수와 제주 동 평균 (개) | S1 |
| `myStoreVsArea` | bar | 내 가게와 동네 외식 결제의 전년 대비 변화 (%) | 입력 + S3 |

**Evidence**

| 필드 | 타입 | 설명 |
|---|---|---|
| `tool` | string | `competition`, `areaCustomers`, `industryTourism` |
| `source` | string | 예: `제주관광빅데이터플랫폼 지역별 소비` |
| `period` | string | 예: `2025-10 ~ 2026-09` |
| `scope` | string | `AREA`, `RADIUS`, `JEJU` |
| `lowSample` | boolean | 표본 부족으로 범위를 넓혔는지 |

### 예시 — 답변 (시나리오 B)

숫자는 형식 예시다.

```json
{
  "type": "ANSWER",
  "summary": "애월읍은 외식 손님의 절반 이상이 관광객이라, 겨울 매출 감소는 상권 구조상 자연스러운 흐름에 가깝습니다.",
  "sections": [
    { "id": "customers", "title": "동네 손님", "text": "애월읍 외식 결제의 58.4%가 내국인 관광객, 10.4%가 외국인 관광객입니다. 결제가 가장 많은 달은 가장 적은 달의 2.1배였습니다." },
    { "id": "industry", "title": "업종 성격", "text": "카페 업종은 제주 전체로 1~2월 결제가 연평균보다 18% 낮습니다." },
    { "id": "tourism", "title": "관광 흐름", "text": "최근 4주 입도객은 작년 같은 기간보다 내국인 3.1% 감소, 외국인 12.4% 증가했습니다." },
    { "id": "advice", "title": "고려해 볼 점", "text": "도민 손님 비중을 높이거나 1~3월 운영 규모를 조정하는 방법을 검토해 볼 수 있습니다." }
  ],
  "charts": [
    {
      "id": "customerMix", "type": "doughnut", "title": "애월읍 외식 손님 구성 (최근 12개월)",
      "labels": ["도민", "내국인 관광객", "외국인 관광객"],
      "datasets": [{ "label": "결제액 비중", "data": [31.2, 58.4, 10.4] }],
      "unit": "%", "source": "제주관광빅데이터플랫폼 지역별 소비, 2025-10 ~ 2026-09, 음식점업 전체", "scope": "AREA"
    }
  ],
  "evidence": [
    { "tool": "areaCustomers", "source": "제주관광빅데이터플랫폼 지역별 소비", "period": "2025-10 ~ 2026-09", "scope": "AREA", "lowSample": false },
    { "tool": "industryTourism", "source": "제주데이터허브 업종별 성별 카드 이용 정보", "period": "2024-07 ~ 2025-06", "scope": "JEJU", "lowSample": false }
  ],
  "limits": [
    "동네 손님 구성은 음식점업 전체 기준입니다.",
    "업종 성격은 제주 전체 기준입니다.",
    "카드 데이터는 BC카드 원천이며 점유율 보정 추정치입니다.",
    "과거 데이터 기반이며 예측이 아닙니다."
  ],
  "suggestions": ["도민 비중이 높은 인접 동과 비교해 볼까요?", "외국인 손님은 어느 나라가 많은지 볼까요?"]
}
```

### 예시 — 거절

```json
{
  "type": "REFUSE",
  "summary": "시간대별 매출 데이터는 없어 저녁 장사 여부는 판단할 수 없습니다.",
  "sections": [],
  "charts": [],
  "evidence": [],
  "limits": [],
  "suggestions": ["이 동네 손님이 도민인지 관광객인지 볼까요?", "계절별 매출 흐름을 볼까요?"]
}
```

### 예시 — 되묻기

```json
{
  "type": "ASK_BACK",
  "summary": "작년과 비교하려면 최근 월매출과 작년 같은 달 매출을 입력해 주세요.",
  "sections": [], "charts": [], "evidence": [], "limits": [], "suggestions": [],
  "askFor": "MY_STORE"
}
```

### 에러

| 상태 | `code` | 언제 | 화면 동작 |
|---|---|---|---|
| 400 | `INVALID_REQUEST` | 필수 필드 없음, 코드값이 목록에 없음, 500자 초과 | 입력 확인 안내 |
| 503 | `LLM_UNAVAILABLE` | LLM API 오류, 시간 초과 | "잠시 후 다시 시도해 주세요" |
| 500 | `INTERNAL_ERROR` | 그 외 | 같은 안내 |

```json
{ "code": "INVALID_REQUEST", "message": "area 값이 올바르지 않습니다.", "field": "area" }
```

### 응답 시간

LLM이 도구를 여러 번 부르므로 10~30초 걸릴 수 있다. 화면은 로딩 표시를 하고, 요청 타임아웃은 60초로 둔다. 서버는 LLM 호출 타임아웃을 50초로 둔다.

---

## 4. 데이터 뷰 (① → ②)

`data/sql/04_views.sql`에서 만든다. ②는 이 뷰만 조회한다. 원본 테이블을 직접 읽지 않는다.

### v_store — 가게 (S1)

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `store_id` | VARCHAR | S1 상가업소번호 (없으면 행 번호) |
| `store_name` | VARCHAR | 상호명 |
| `emd_code` | VARCHAR | 행정동 코드 8자리 |
| `emd_name` | VARCHAR | 행정동명 |
| `industry` | VARCHAR | 7개 업종 코드. 7개에 해당하지 않는 가게는 뷰에서 제외 |
| `lon` | DOUBLE | 경도 |
| `lat` | DOUBLE | 위도 |

### v_area_spend — 동네 소비 (S3)

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `ym` | VARCHAR | `YYYY-MM` |
| `emd_code` | VARCHAR | 행정동 코드 8자리 (`ref_area`로 변환) |
| `emd_name` | VARCHAR | 행정동명 |
| `customer_type` | VARCHAR | `RESIDENT`(도민), `DOMESTIC`(내국인 관광객), `FOREIGN`(외국인 관광객) |
| `category` | VARCHAR | `FOOD`(음식점업), `RETAIL`, `LODGING`, `LEISURE`, `SERVICE` |
| `txn_count` | BIGINT | 이용건수 |
| `amount` | BIGINT | 소비금액 (원) |
| `low_sample` | BOOLEAN | 이용건수가 기준 미만이면 true |

### v_industry_spend — 업종 카드 (S2)

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `ym` | VARCHAR | `YYYY-MM` |
| `industry` | VARCHAR | 7개 업종 코드 (`PUB`은 주점 두 업종 합산) |
| `user_type` | VARCHAR | `RESIDENT`(제주도민), `DOMESTIC`(내국인), `CORP`(법인), `CHINA`, `JAPAN`, `SEA`(동남아), `OTHER`(기타외국) |
| `user_count` | BIGINT | 이용자 수 (성별 합산) |
| `txn_count` | BIGINT | 이용 건수 |
| `amount` | BIGINT | 이용 금액 (원) |

### v_arrivals — 입도 관광객 (S4)

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `dt` | DATE | 날짜 |
| `domestic_cnt` | INTEGER | 내국인 |
| `foreign_cnt` | INTEGER | 외국인 |
| `total_cnt` | INTEGER | `domestic_cnt + foreign_cnt`로 다시 계산한 값 |

`date`, `foreign`은 SQL 예약어라 컬럼 이름으로 쓰지 않는다.

### 참조 테이블 (02_mapping.sql)

| 테이블 | 컬럼 |
|---|---|
| `ref_area` | `emd_code`, `emd_name`, `city`, `s3_name` |
| `ref_industry` | `industry`, `name_ko`, `s1_small_category`(여러 행 가능), `s2_name`(여러 행 가능) |
| `filter_log` | `rule`, `source`, `dropped_rows` |

---

## 5. 도구 결과 record (② → ③)

`metric` 패키지에 둔다. ③은 이 record를 그대로 LLM 도구 결과로 넘기고, 차트와 한계 문구를 만든다.

### 공통

```java
public record MetricMeta(
    String source,      // "제주관광빅데이터플랫폼 지역별 소비"
    String period,      // "2025-10 ~ 2026-09"
    String scope,       // "AREA" | "RADIUS" | "JEJU"
    boolean lowSample,  // 표본 부족으로 기간을 넓혔으면 true
    String note         // 추가 설명, 없으면 null
) {}

public record MonthlyPoint(String ym, Double value) {}  // value null = 표본 부족

public record SeriesPoint(String label, Double value) {}
```

### competition

```java
// competition(String area, String industry, Integer radiusM)  radiusM null이면 동 단위만
public record CompetitionResult(
    String area, String industry,
    int storeCount,          // 동 안의 같은 업종 점포 수 (개)
    double jejuAvg,          // 제주 43개 동 평균 점포 수 (개)
    double densityRatio,     // storeCount / jejuAvg (배)
    Integer radiusM,         // 요청 반경 (m), 없으면 null
    Integer radiusCount,     // 동 중심 반경 안 점포 수 (개), 없으면 null
    MetricMeta meta
) {}
```

### areaCustomers

```java
// areaCustomers(String area, int months)  months 기본 12, 표본 부족이면 24로 다시
public record AreaCustomerResult(
    String area, String period,
    double residentShare,        // 도민 결제액 비중 (%)
    double domesticShare,        // 내국인 관광객 비중 (%)
    double foreignShare,         // 외국인 관광객 비중 (%)
    double jejuAvgTouristShare,  // 제주 43개 동 평균 관광객 비중 (%)
    Long residentTicket,         // 도민 건당 결제액 (원)
    Long touristTicket,          // 관광객 건당 결제액 (원)
    double seasonalRange,        // 최근 12개월 최대월 / 최소월 (배)
    Double yoyChange,            // 최신월 결제액 전년 동월 대비 (%)
    Double txnPerStore,          // 2025-09 음식점업 결제 건수 / 2025-09 음식 점포 수 (건)
    List<MonthlyPoint> residentMonthly,  // 월별 도민 결제액 (원)
    List<MonthlyPoint> touristMonthly,   // 월별 관광객 결제액 (원)
    List<MonthlyPoint> jejuAvgMonthly,   // 월별 제주 동 평균 결제액 (원)
    TouristFlow touristFlow,
    MetricMeta meta
) {}

public record TouristFlow(
    String period,               // "2026-10-16 ~ 2026-11-12"
    double domesticYoy,          // 최근 4주 내국인 입도객 전년 대비 (%)
    double foreignYoy,           // 최근 4주 외국인 입도객 전년 대비 (%)
    List<SeriesPoint> thisYear,  // 일별 입도객 (명)
    List<SeriesPoint> lastYear   // 작년 같은 기간 일별 (명)
) {}
```

### industryTourism

```java
// industryTourism(String industry, int months)  months 기본 12
public record IndustryTourismResult(
    String industry, String period,
    double residentShare,                  // 제주도민 결제액 비중 (%)
    double domesticShare,                  // 내국인 (%)
    double foreignShare,                   // 외국인 합계 (%)
    Map<String, Double> foreignByCountry,  // CHINA, JAPAN, SEA, OTHER → 외국인 중 비중 (%)
    List<MonthlyPoint> seasonIndex,        // 월별 지수, 연평균 = 100
    Double ticketYoy,                      // 건당 금액 전년 대비 (%)
    MetricMeta meta
) {}
```

법인(`CORP`) 결제는 비중 계산에서 뺀다.

---

## 6. LLM 구조화 출력 (③ 내부)

LLM에게 받는 모양이다. 숫자 필드가 없다는 점이 핵심이다.

```java
public record AgentReply(
    String type,                 // ANSWER | REFUSE | ASK_BACK
    String summary,
    List<SectionText> sections,  // {id, title, text}
    List<String> chartIds,       // 3장 차트 ID 중 최대 3개
    List<String> suggestions,
    String askFor                // ASK_BACK일 때만
) {}
```

`ChatResponse`의 `charts`, `evidence`, `limits`는 `AgentReply`가 아니라 `EvidenceCollector`에 모인 도구 결과로 코드가 만든다.

---

## 7. 평가 질문 형식 (`eval/questions.yaml`)

```yaml
- id: Q01
  scenario: B                 # A | B | C | REFUSE | ASK_BACK
  request:
    userType: EXISTING
    area: "50110250"
    industry: CAFE
    myStore: null
    message: "애월에서 카페 하는데 겨울만 되면 매출이 반토막 나요. 정상인가요?"
  expect:
    type: ANSWER
    tools: [areaCustomers, industryTourism]
    numbers:                  # 응답 sections 본문에 나와야 하는 숫자
      - gold: Q01_tourist_share.sql
        tolerance: 0.5        # 허용 오차 (단위 그대로)
- id: Q21
  scenario: REFUSE
  request: { userType: STARTUP, area: "50110660", industry: CAFE, myStore: null, message: "저녁 장사는 어때요?" }
  expect:
    type: REFUSE
    tools: []
```

---

## 변경 이력

| 버전 | 날짜 | 내용 | 승인 |
|---|---|---|---|
| 0.1 | 11/13 | 초안 | |
