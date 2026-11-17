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

-- S2 걸러내기 기준
--  (1) 집계값이 비었거나 0 이하이면 버린다. (실제로는 0건)
--  (2) 성별 '알수없음'은 버리지 않는다. v_industry_spend는 성별을 합산하므로
--      합계에 그대로 쓴다. 성별로 나누는 지표는 계약에 없다.
--  (3) 외식업 7개 업종에 들지 않는 36개 업종은 버리는 것이 아니라
--      v_industry_spend에서 제외된다. 규모를 알 수 있게 수만 기록한다.
INSERT INTO filter_log
SELECT '집계값 없음', 'S2', count(*) FROM raw_s2
WHERE user_count IS NULL OR use_count IS NULL OR use_amount IS NULL;

INSERT INTO filter_log
SELECT '이용금액 또는 건수가 0 이하', 'S2', count(*) FROM raw_s2
WHERE use_amount <= 0 OR use_count <= 0;

INSERT INTO filter_log
SELECT '외식업 7개 업종 밖(뷰에서 제외)', 'S2', count(*)
FROM raw_s2 r
WHERE NOT EXISTS (
    SELECT 1 FROM ref_industry i WHERE i.s2_name = r.biz_nm
);

CREATE OR REPLACE TABLE clean_s2 AS
SELECT * FROM raw_s2
WHERE user_count IS NOT NULL
  AND use_count IS NOT NULL
  AND use_amount IS NOT NULL
  AND use_amount > 0
  AND use_count > 0;

-- S3 걸러내기 기준
--  (1) 이용건수나 소비금액이 비었거나 0 이하이면 버린다.
--  (2) 건당 금액이 100만 원을 넘으면 입력 오류로 보고 버린다.
--      팀 규칙은 "상하위 1%"를 예로 들었으나 실제 분포는 p01 3,123원 / p99 125,140원으로
--      둘 다 음식점업에서 있을 수 있는 값이다. 1%를 자르면 분식집과 고급 횟집이 함께 사라진다.
--      명백한 오류만 버리는 쪽을 택했고, 해당 행은 음식점업 기준 28건이다.
--  (3) 표본 부족(low_sample)은 버리지 않는다. 04_views.sql에서 계약 단위로 합친 뒤
--      이용건수 100건 미만을 true로 표시하고, 비율 계산에서 빼는 것은 ②가 판단한다.
INSERT INTO filter_log
SELECT '집계값 없음', 'S3', count(*) FROM raw_s3
WHERE 이용건수 IS NULL OR 소비금액 IS NULL;

INSERT INTO filter_log
SELECT '이용건수 또는 소비금액이 0 이하', 'S3', count(*) FROM raw_s3
WHERE 이용건수 <= 0 OR 소비금액 <= 0;

INSERT INTO filter_log
SELECT '건당 금액 100만 원 초과(입력 오류로 판단)', 'S3', count(*) FROM raw_s3
WHERE 이용건수 > 0 AND 소비금액 * 1.0 / 이용건수 > 1000000;

CREATE OR REPLACE TABLE clean_s3 AS
SELECT * FROM raw_s3
WHERE 이용건수 IS NOT NULL
  AND 소비금액 IS NOT NULL
  AND 이용건수 > 0
  AND 소비금액 > 0
  AND 소비금액 * 1.0 / 이용건수 <= 1000000;

-- S4 걸러내기 기준
--  (1) 날짜나 방문객 수가 비면 버린다.
--      빈 값은 전부 2013~2017년 구간이고 2018년 이후는 하나도 없다.
--      지표는 최근 28일과 그 364일 전을 보므로 분석 구간에는 영향이 없다.
--  (2) 전체방문객수는 원본을 믿지 않고 내국인 + 외국인으로 다시 계산한다.
--      값이 모두 있는 날만 대상으로 불일치 수를 센다.
INSERT INTO filter_log
SELECT '방문객 수 없음(전부 2013~2017년)', 'S4', count(*) FROM raw_s4
WHERE dt IS NULL OR domestic_cnt IS NULL OR foreign_cnt IS NULL;

INSERT INTO filter_log
SELECT '전체방문객수가 내국인+외국인과 불일치(재계산함)', 'S4', count(*) FROM raw_s4
WHERE dt IS NOT NULL AND domestic_cnt IS NOT NULL AND foreign_cnt IS NOT NULL
  AND total_cnt_원본 IS DISTINCT FROM domestic_cnt + foreign_cnt;

CREATE OR REPLACE TABLE clean_s4 AS
SELECT dt, domestic_cnt, foreign_cnt
FROM raw_s4
WHERE dt IS NOT NULL AND domestic_cnt IS NOT NULL AND foreign_cnt IS NOT NULL;
