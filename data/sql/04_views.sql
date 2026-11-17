-- 04_views.sql — ②가 조회하는 뷰. 계약은 docs/API명세.md 4장. (① 소유)
-- ②는 이 뷰만 읽는다. 원본 테이블을 직접 읽지 않는다.
-- 컬럼 이름과 타입을 바꿀 때는 ②에게 먼저 알린다.

-- v_store — 가게 (S1)
-- 7개 업종에 해당하지 않는 가게는 뷰에서 제외한다. (계약)
-- S1에 상가업소번호 컬럼이 없어 store_id는 clean_s1에서 매긴 행 번호를 쓴다.
CREATE OR REPLACE VIEW v_store AS
SELECT CAST(c.store_seq AS VARCHAR) AS store_id,
       c.상호명        AS store_name,
       c.행정동코드    AS emd_code,
       c.행정동명      AS emd_name,
       i.industry      AS industry,
       c.경도          AS lon,
       c.위도          AS lat
FROM clean_s1 c
JOIN (
    SELECT DISTINCT industry, s1_small_category
    FROM ref_industry
    WHERE s1_small_category IS NOT NULL
) i ON i.s1_small_category = c.상권업종소분류명
WHERE c.상권업종대분류명 = '음식';

-- v_industry_spend — 업종 카드 (S2)
-- 성별은 합산한다. PUB은 '일반유흥 주점업'과 '기타 주점업'을 GROUP BY로 합친다.
-- 전체 기간(2018-01~2025-06)을 그대로 둔다. 분석 기간은 ②가 months로 자른다.
CREATE OR REPLACE VIEW v_industry_spend AS
SELECT substr(c.base_year_month, 1, 4) || '-' || substr(c.base_year_month, 5, 2) AS ym,
       i.industry AS industry,
       CASE c.user_type
           WHEN '제주도민' THEN 'RESIDENT'
           WHEN '내국인'   THEN 'DOMESTIC'
           WHEN '법인'     THEN 'CORP'
           WHEN '중국'     THEN 'CHINA'
           WHEN '일본'     THEN 'JAPAN'
           WHEN '동남아'   THEN 'SEA'
           WHEN '기타외국' THEN 'OTHER'
       END AS user_type,
       SUM(c.user_count) AS user_count,
       SUM(c.use_count)  AS txn_count,
       SUM(c.use_amount) AS amount
FROM clean_s2 c
JOIN (
    SELECT DISTINCT industry, s2_name
    FROM ref_industry
    WHERE s2_name IS NOT NULL
) i ON i.s2_name = c.biz_nm
GROUP BY 1, 2, 3;

-- v_area_spend — 동네 소비 (S3)
-- S3는 같은 조합에 행이 여럿이므로 계약 단위로 합친다.
-- 지역 키는 S1의 행안부 행정동코드 8자리다. S3의 7자리 자체코드는 쓰지 않고 이름으로 잇는다.
-- low_sample: 합친 뒤 이용건수 100건 미만. 건당 금액 추정이 흔들리는 구간을 표시만 한다.
--   음식점업 10,365칸 중 657칸(6.3%)이 해당하고 전부 외국인 관광객이다.
--   도민과 내국인 관광객은 음식점업에서 해당 칸이 없다.
CREATE OR REPLACE VIEW v_area_spend AS
SELECT c.기준연월_ym AS ym,
       c.emd_code,
       c.emd_name,
       c.customer_type,
       c.category,
       c.txn_count,
       c.amount,
       c.txn_count < 100 AS low_sample
FROM (
    SELECT substr(s.기준연월, 1, 4) || '-' || substr(s.기준연월, 5, 2) AS 기준연월_ym,
           a.emd_code   AS emd_code,
           a.emd_name   AS emd_name,
           CASE s.관광객구분
               WHEN '도민'          THEN 'RESIDENT'
               WHEN '내국인 관광객' THEN 'DOMESTIC'
               WHEN '외국인 관광객' THEN 'FOREIGN'
           END AS customer_type,
           CASE s.업종명
               WHEN '음식점업'         THEN 'FOOD'
               WHEN '소매업'           THEN 'RETAIL'
               WHEN '숙박업'           THEN 'LODGING'
               WHEN '예술스포츠여가업' THEN 'LEISURE'
               WHEN '기타서비스업'     THEN 'SERVICE'
           END AS category,
           SUM(s.이용건수) AS txn_count,
           SUM(s.소비금액) AS amount
    FROM clean_s3 s
    JOIN ref_area a ON a.s3_name = s.읍면동명
    GROUP BY 1, 2, 3, 4, 5
) c;

-- v_arrivals — 입도 관광객 (S4)
-- total_cnt는 원본을 쓰지 않고 내국인 + 외국인으로 다시 계산한다.
CREATE OR REPLACE VIEW v_arrivals AS
SELECT dt,
       domestic_cnt,
       foreign_cnt,
       domestic_cnt + foreign_cnt AS total_cnt
FROM clean_s4;
