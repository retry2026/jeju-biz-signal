# 지표 계산 명세

API 계약은 `docs/API명세.md` 4~5장을 따른다. 지표는 명시된 뷰만 조회하며, 조회 결과와 중간 계산에 원본 테이블을 사용하지 않는다.

## 공통 규칙

- 비율(%)·배수·지수는 계산 후 소수 첫째 자리까지 반올림한다.
- 금액은 원 단위 정수로 반환한다. 건당 금액은 합계 금액을 합계 건수로 나눈 뒤 원 단위 정수로 반올림한다.
- 조회 기간은 시스템 날짜가 아니라 해당 뷰에서 조회되는 최신 `ym`을 기준으로 정한다.
- 0으로 나누지 않는다. 결과 타입이 nullable이면 `null`을 반환한다. API record의 primitive `double` 필드처럼 `null`을 표현할 수 없는 필수 결과의 분모가 0이면 명시적으로 예외를 던진다. 이를 0이나 `NaN`으로 대체하지 않는다.
- 해당 기간에 값이 없거나 비교 기준이 없어 계산할 수 없는 nullable 결과는 `null`로 둔다. 실제 집계값이 0인 경우와 데이터 누락은 구분한다.
- 존재하지 않는 지역·업종 코드는 `IllegalArgumentException`으로 처리한다.

## `competition`

- 사용 뷰: `v_store`만 사용한다. 업종별 점포 수는 `industry`가 요청 코드와 같은 행을 센다.
- `storeCount`: 요청한 `emd_code`와 업종의 점포 수.
- `jejuAvg`: `v_store`에 나타나는 각 `emd_code`를 모집단으로 삼는다. 요청 업종 점포가 없는 동은 0개로 포함하고, 동별 점포 수의 평균을 계산한다.
- `densityRatio = storeCount / jejuAvg`.
- `radiusM`이 `null`이면 `radiusCount`도 `null`이다. 양수인 `radiusM`을 요청하면 대상 동의 `v_store` 좌표 평균을 동 중심으로 추정하고, 같은 업종의 점포 중 중심에서 반경 이내인 수를 센다. 거리는 지구 반지름 6,371,000m를 적용한 하버사인 공식으로 계산한다. 좌표가 없는 행은 거리 계산에서 제외한다.
- `jejuAvg`가 0이면 `densityRatio`를 계산할 수 없으므로 예외를 던진다. 반경을 요청했지만 대상 동 중심 좌표를 구할 수 없는 경우 `radiusCount`는 `null`이다.
- `meta.scope`는 반경 미지정 시 `AREA`, 지정 시 `RADIUS`다. `meta.source`는 `"제주데이터허브 제주 상가(상권) 정보"`이며, 뷰에 기간 정보가 없으므로 `meta.period`는 `null`이다.

## `areaCustomers`

- 사용 뷰: 소비 집계와 제주 동별 평균은 `v_area_spend`, 점포 수는 `v_store`, 관광 흐름은 `v_arrivals`를 사용한다.
- 분석 대상은 요청 동의 `category = 'FOOD'`다. 결제액과 건수는 `customer_type`별로 집계한다. 도민은 `RESIDENT`, 관광객은 `DOMESTIC`과 `FOREIGN`의 합이다.
- 비중은 표본이 유효한 결제액 합계를 분자로, 같은 유효 범위의 전체 결제액 합계를 분모로 계산한다. 관광객 비중은 `DOMESTIC + FOREIGN` 비중이다. 제주 평균 관광객 비중은 동별 관광객 비중을 먼저 계산한 뒤 유효 동의 비율을 평균한다.
- `months <= 1`이면 `IllegalArgumentException`을 던진다. `months`가 12일 때는 최신 12개월의 요청 동, `category = 'FOOD'` 행만 확인한다. 이 범위에 `low_sample = true`가 하나라도 있으면 최신 24개월을 다시 조회한다. 다른 카테고리의 표본 부족은 확장 조건에 포함하지 않는다. 기간을 넓혔으면 `meta.lowSample = true`로 기록하고 결과 `period`에도 실제 사용 기간을 표시한다. 24개월 조회에서도 표본 부족인 행은 유효 표본으로 간주하지 않는다.
- `residentMonthly`, `touristMonthly`, `jejuAvgMonthly`는 해당 월의 필수 집계값이 없거나 그 집계에 필요한 행이 `low_sample = true`이면 해당 `MonthlyPoint.value`를 `null`로 둔다. 결측을 0원으로 채우지 않는다.
- `seasonalRange`는 조회 기간이 12개월이든 표본 부족으로 24개월로 확장됐든 항상 최신 12개월의 유효 월별 전체 결제액 중 최댓값을 최솟값으로 나눈다. 확장된 24개월 전체를 계산 범위로 사용하지 않는다. 최솟값이 0이거나 유효 월이 없어 계산할 수 없으면 primitive 필드에 임의의 값을 넣지 않고 예외를 던진다.
- `yoyChange`는 최신 유효 월의 전체 결제액을 전년 동월 전체 결제액과 비교한 증감률이다. 어느 한쪽 값이 없거나 전년 동월 금액이 0이면 `null`이다.
- `residentTicket`과 `touristTicket`은 각각 유효한 결제액 합계를 해당 결제 건수 합계로 나누고 원 단위 정수로 반올림한다. 건수가 없으면 nullable 필드에 `null`을 둔다.
- `txnPerStore`는 기간과 무관하게 2025-09의 `FOOD` 결제 건수 합계를 같은 동의 `v_store` 음식점 점포 수로 나눈다. 점포 수가 없거나 0이면 `null`이다.
- `touristFlow`는 `v_arrivals`의 최신 `dt`를 포함한 최근 28일을 올해 기간으로 조회한다. 작년 기간은 올해 각 날짜보다 정확히 364일 전인 28일이며, 요일이 일치하도록 날짜를 대응한다. 두 기간 모두 일별 행이 모두 있어야 한다. 기간 누락이나 작년 합계가 0이면 예외를 던진다.
- `domesticYoy`와 `foreignYoy`는 각각 `(올해 28일 합계 / 작년 대응 28일 합계 - 1) × 100`이며 소수 첫째 자리까지 반올림한다. 합계는 각각 `domestic_cnt`, `foreign_cnt`를 사용한다.
- `touristFlow.thisYear`와 `lastYear`는 해당 기간의 각 날짜를 오름차순으로 담는다. `SeriesPoint.label`은 `dt`의 `YYYY-MM-DD` 문자열, `value`는 그 날짜의 `total_cnt`다. 누락된 날짜를 0명으로 보충하지 않는다.
- primitive 비중 필드의 필수 분모가 0이면 예외를 던진다. `MetricMeta.period`는 실제 소비 분석 기간, `scope`는 `AREA`, `source`는 `"제주관광빅데이터플랫폼 지역별 소비"`로 기록한다.

## `industryTourism`

- 사용 뷰: `v_industry_spend`만 사용한다. 요청 업종의 데이터를 최신 `ym` 기준 `months`개월로 집계한다. `months`는 양수여야 하며 기본 분석 기간은 12개월이다.
- `residentShare`, `domesticShare`, `foreignShare`는 기간 내 `amount` 합계로 계산한다. 분모는 `CORP`를 제외한 전체 결제액, 분자는 각각 `RESIDENT`, `DOMESTIC`, 그리고 `CHINA`, `JAPAN`, `SEA`, `OTHER` 합계다.
- `foreignByCountry`는 외국인 결제액 합계를 분모로 각 국가 코드의 비중을 계산한다. 외국인 결제액이 0이면 네 국가 값은 각각 `null`이다.
- 기간에 `RESIDENT`, `DOMESTIC`, 또는 외국인 국가 코드 행이 하나도 없어 primitive 비중을 계산할 입력을 구분할 수 없으면 명시적으로 예외를 던진다. 외국인 국가 중 개별 코드 행만 없는 경우 해당 `foreignByCountry` 값은 `null`이다.
- 법인 제외 규칙은 비중 계산에 적용한다. 계절 지수와 건당 금액 증감률은 조회 기간의 전체 user type 행을 사용한다.
- `seasonIndex`는 각 `ym`의 전체 결제액 합계를 구하고, 유효 월별 결제액 평균 대비 해당 월 금액의 비율에 100을 곱한다. 값이 없는 월은 `MonthlyPoint.value = null`이며, 평균 결제액이 0이면 해당 월 지수는 `null`이다. 유효 월들의 지수 평균은 100이다.
- `ticketYoy`는 최신 월과 전년 동월의 건당 금액을 비교한다. 건당 금액은 해당 월 전체 `amount` 합계를 전체 `txn_count` 합계로 나눈 값이다. 전년 동월이 없거나 양쪽 중 필요한 건수가 없거나 전년 동월 건당 금액이 0이면 `null`이다. 그 외에는 `(최신 건당 금액 / 전년 동월 건당 금액 - 1) × 100`으로 계산한다.
- 조회 결과에 해당 업종 코드가 없으면 `IllegalArgumentException`을 던진다. 비중의 분모가 0이면 primitive 결과 필드에 0을 넣지 않고 예외를 던진다.
- `MetricMeta.period`는 실제 사용 기간, `scope`는 `JEJU`, `source`는 `"제주데이터허브 업종별 성별 카드 이용 정보"`, `lowSample`은 `false`로 기록한다. 뷰에 없는 기간·추가 설명은 `null`이다.
