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
