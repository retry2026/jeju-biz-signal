-- 02_mapping.sql — 참조 테이블. 코드 체계를 하나로 맞춘다. (① 소유)
-- 계약: docs/API명세.md 4장 "참조 테이블"

-- 외식업 7개 업종.
-- S1은 상권업종 중분류로 가른다. 다만 '기타 간이'에는 치킨·분식·빵처럼
-- S2에 대응 업종이 없는 것이 섞여 있어 FASTFOOD는 소분류(피자, 버거)로만 좁힌다.
-- s1_small_category / s2_name 은 각각 여러 행으로 들어간다. (계약)
CREATE OR REPLACE TABLE ref_industry AS
WITH names(industry, name_ko) AS (
    VALUES ('CAFE', '카페'),
           ('KOREAN', '한식 (고깃집 포함)'),
           ('CHINESE', '중식'),
           ('JAPANESE', '일식'),
           ('WESTERN', '양식'),
           ('PUB', '주점'),
           ('FASTFOOD', '패스트푸드')
),
s1_small AS (
    SELECT DISTINCT 상권업종중분류명 AS mid, 상권업종소분류명 AS small
    FROM raw_s1
    WHERE 상권업종대분류명 = '음식'
      AND 상권업종소분류명 IS NOT NULL
),
s1_map AS (
    SELECT CASE mid
               WHEN '비알코올' THEN 'CAFE'
               WHEN '한식'     THEN 'KOREAN'
               WHEN '중식'     THEN 'CHINESE'
               WHEN '일식'     THEN 'JAPANESE'
               WHEN '서양식'   THEN 'WESTERN'
               WHEN '주점'     THEN 'PUB'
               ELSE CASE WHEN mid = '기타 간이' AND small IN ('피자', '버거')
                         THEN 'FASTFOOD' END
           END AS industry,
           small AS s1_small_category
    FROM s1_small
),
s2_map(industry, s2_name) AS (
    VALUES ('CAFE', '비알콜 음료점업'),
           ('KOREAN', '한식 음식점업'),
           ('CHINESE', '중식 음식점업'),
           ('JAPANESE', '일식 음식점업'),
           ('WESTERN', '서양식 음식점업'),
           ('PUB', '일반유흥 주점업'),
           ('PUB', '기타 주점업'),
           ('FASTFOOD', '피자 햄버거 샌드위치 및 유사 음식점업')
)
SELECT n.industry, n.name_ko,
       m.s1_small_category,
       CAST(NULL AS VARCHAR) AS s2_name
FROM names n
JOIN s1_map m ON m.industry = n.industry
UNION ALL
SELECT n.industry, n.name_ko,
       CAST(NULL AS VARCHAR) AS s1_small_category,
       s.s2_name
FROM names n
JOIN s2_map s ON s.industry = n.industry;

-- 읍면동 43개. 기준 키는 S1의 행안부 행정동코드 8자리다.
-- S3는 자체 7자리 코드를 쓰므로 이름(s3_name)으로 잇는다.
-- S1 행정동명 43개와 S3 읍면동명 43개가 모두 일치하는 것을 확인했다. (2026-11-17)
CREATE OR REPLACE TABLE ref_area AS
SELECT DISTINCT
    행정동코드 AS emd_code,
    행정동명   AS emd_name,
    시군구명   AS city,
    행정동명   AS s3_name
FROM raw_s1
WHERE 행정동코드 IS NOT NULL
  AND 행정동명 IS NOT NULL;
