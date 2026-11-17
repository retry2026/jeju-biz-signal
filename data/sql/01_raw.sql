-- 01_raw.sql — 원본 CSV를 그대로 테이블로 올린다. (① 소유)
-- ${DATA_DIR}는 DataLoader가 app.data-dir의 절대경로로 바꾼다.
-- 값은 여기서 고치지 않는다. 걸러내기는 03_clean.sql에서만 한다.
--
-- 문자열 정리: S1 원본은 값 끝에 공백이 붙어 있는 경우가 있다.
-- 예를 들어 상권업종중분류명이 '비알코올'이 아니라 '비알코올 '로 들어와 있어
-- 그대로 비교하면 매핑이 통째로 어긋난다. 적재 시점에 trim으로 맞춘다.
-- nfc_normalize는 CP949→UTF-8 변환 과정의 자모 분리를 함께 막는다.

-- S1 제주특별자치도 상가(상권) 정보 (제주데이터허브 793, 2025-09-18 스냅샷)
CREATE OR REPLACE TABLE raw_s1 AS
SELECT trim(nfc_normalize(상호명))           AS 상호명,
       trim(nfc_normalize(상권업종대분류명)) AS 상권업종대분류명,
       trim(nfc_normalize(상권업종중분류명)) AS 상권업종중분류명,
       trim(nfc_normalize(상권업종소분류명)) AS 상권업종소분류명,
       trim(nfc_normalize(표준산업분류명))   AS 표준산업분류명,
       trim(nfc_normalize(시도명))           AS 시도명,
       trim(nfc_normalize(시군구명))         AS 시군구명,
       trim(nfc_normalize(행정동명))         AS 행정동명,
       trim(nfc_normalize(법정동명))         AS 법정동명,
       행정동코드,
       법정동코드,
       trim(nfc_normalize(지번주소))         AS 지번주소,
       trim(nfc_normalize(도로명주소))       AS 도로명주소,
       신우편번호,
       경도,
       위도
FROM read_csv(
    '${DATA_DIR}/raw/s1_store.csv',
    header = true,
    delim = ',',
    quote = '"',
    types = {
        '행정동코드': 'VARCHAR',
        '법정동코드': 'VARCHAR',
        '신우편번호': 'VARCHAR',
        '경도': 'DOUBLE',
        '위도': 'DOUBLE'
    }
);

-- S2 업종별 성별 카드 이용 정보 (제주데이터허브 1048, 제주빅데이터센터)
-- 2018-01 ~ 2025-06 월별. 지역 구분이 없는 제주 전체 집계다.
CREATE OR REPLACE TABLE raw_s2 AS
SELECT CAST(base_year_month AS VARCHAR)  AS base_year_month,
       trim(nfc_normalize(biz_nm))       AS biz_nm,
       trim(nfc_normalize(user_type))    AS user_type,
       trim(nfc_normalize(sex))          AS sex,
       user_count,
       use_count,
       use_amount
FROM read_csv(
    '${DATA_DIR}/raw/s2_industry_card.csv',
    header = true,
    delim = ',',
    quote = '"',
    types = {
        'base_year_month': 'VARCHAR',
        'user_count': 'BIGINT',
        'use_count': 'BIGINT',
        'use_amount': 'BIGINT'
    }
);

-- S3 제주 지역별 소비 (제주관광빅데이터플랫폼, 제주관광공사 / 공공누리 제1유형)
-- 2020-01 ~ 2026-09 월별.
-- 주의: 명세에는 "한 달 × 한 읍면동 × 한 관광객구분 × 한 업종"이 한 행이라고 돼 있으나,
-- 실제로는 같은 조합에 행이 여럿이다(노형동 2026-09 내국인 음식점업 15행).
-- 값이 모두 달라 중복이 아니라 CSV에 드러나지 않은 세부 분할로 보인다.
-- 04_views.sql에서 계약 단위로 SUM해 합친다.
CREATE OR REPLACE TABLE raw_s3 AS
SELECT CAST(기준연월 AS VARCHAR)       AS 기준연월,
       trim(nfc_normalize(관광객구분)) AS 관광객구분,
       CAST(읍면동코드 AS VARCHAR)     AS 읍면동코드,
       trim(nfc_normalize(읍면동명))   AS 읍면동명,
       trim(nfc_normalize(지역명))     AS 지역명,
       trim(nfc_normalize(업종명))     AS 업종명,
       이용건수,
       "소비금액(원)"                  AS 소비금액
FROM read_csv(
    '${DATA_DIR}/raw/s3_area_spend.csv',
    header = true,
    delim = ',',
    quote = '"',
    types = {
        '기준연월': 'VARCHAR',
        '읍면동코드': 'VARCHAR',
        '이용건수': 'BIGINT',
        '소비금액(원)': 'BIGINT'
    }
);

-- S4 일별 입도 관광객 (제주관광빅데이터플랫폼, 제주관광협회 / 공공누리 제1유형)
-- 2013-08-01 ~ 2026-11-16. 전체방문객수는 03_clean에서 다시 계산한다.
CREATE OR REPLACE TABLE raw_s4 AS
SELECT strptime("기준일자(YYYYMMDD)", '%Y%m%d')::DATE AS dt,
       내국인방문객수 AS domestic_cnt,
       외국인방문객수 AS foreign_cnt,
       전체방문객수   AS total_cnt_원본
FROM read_csv(
    '${DATA_DIR}/raw/s4_arrivals.csv',
    header = true,
    delim = ',',
    quote = '"',
    types = {
        '기준일자(YYYYMMDD)': 'VARCHAR',
        '내국인방문객수': 'INTEGER',
        '외국인방문객수': 'INTEGER',
        '전체방문객수': 'INTEGER'
    }
);
