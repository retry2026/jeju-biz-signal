-- 03_clean.sql — 걸러내기. 버린 사실과 행 수를 filter_log에 남긴다. (① 소유)
-- 규칙마다 버린 행 수를 기록해 데이터 활용 명세에 그대로 싣는다.

CREATE OR REPLACE TABLE filter_log (
    rule         VARCHAR,
    source       VARCHAR,
    dropped_rows BIGINT
);

-- S1 걸러내기 기준
--  (1) 행정동코드가 없으면 지역에 붙일 수 없다.
--  (2) 업종 소분류가 없으면 7개 업종으로 가를 수 없다.
--  (3) 좌표가 제주 범위를 벗어나면 반경 계산이 틀어진다.
--      범위는 실제 값 분포(경도 126.16~126.97, 위도 33.11~33.96)를 감싸는
--      경도 126.0~127.1, 위도 33.0~34.1로 둔다. 추자면·마라도까지 포함한다.
INSERT INTO filter_log
SELECT '행정동코드 없음', 'S1', count(*) FROM raw_s1 WHERE 행정동코드 IS NULL;

INSERT INTO filter_log
SELECT '업종 소분류 없음', 'S1', count(*) FROM raw_s1 WHERE 상권업종소분류명 IS NULL;

INSERT INTO filter_log
SELECT '좌표 없음', 'S1', count(*) FROM raw_s1 WHERE 경도 IS NULL OR 위도 IS NULL;

INSERT INTO filter_log
SELECT '좌표가 제주 범위 밖', 'S1', count(*) FROM raw_s1
WHERE 경도 IS NOT NULL AND 위도 IS NOT NULL
  AND (경도 NOT BETWEEN 126.0 AND 127.1 OR 위도 NOT BETWEEN 33.0 AND 34.1);

-- 음식 대분류지만 7개 업종에 들지 않는 가게(치킨, 분식, 빵, 구내식당 등)는
-- 버리는 것이 아니라 v_store에서 제외된다. 규모를 알 수 있게 수만 기록한다.
INSERT INTO filter_log
SELECT '음식 대분류이나 7개 업종 밖(뷰에서 제외)', 'S1', count(*)
FROM raw_s1 r
WHERE r.상권업종대분류명 = '음식'
  AND NOT EXISTS (
      SELECT 1 FROM ref_industry i
      WHERE i.s1_small_category = r.상권업종소분류명
  );

CREATE OR REPLACE TABLE clean_s1 AS
SELECT row_number() OVER (ORDER BY 행정동코드, 상권업종소분류명, 상호명, 경도, 위도) AS store_seq,
       *
FROM raw_s1
WHERE 행정동코드 IS NOT NULL
  AND 상권업종소분류명 IS NOT NULL
  AND 경도 IS NOT NULL
  AND 위도 IS NOT NULL
  AND 경도 BETWEEN 126.0 AND 127.1
  AND 위도 BETWEEN 33.0 AND 34.1;
