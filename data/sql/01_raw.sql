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
