-- 한 파일에 여러 문장이 실행되는지 확인하는 테스트용 SQL
CREATE OR REPLACE TABLE t_sample AS SELECT 1 AS x UNION ALL SELECT 2;
CREATE OR REPLACE VIEW v_sample AS SELECT sum(x) AS total FROM t_sample;
