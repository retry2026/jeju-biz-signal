WITH area_codes AS (
    SELECT DISTINCT emd_code
    FROM v_store
),
industry_counts AS (
    SELECT emd_code, COUNT(*) AS store_count
    FROM v_store
    WHERE industry = 'CAFE'
    GROUP BY emd_code
),
district_counts AS (
    SELECT area_codes.emd_code,
           COALESCE(industry_counts.store_count, 0) AS store_count
    FROM area_codes
    LEFT JOIN industry_counts USING (emd_code)
),
counts AS (
    SELECT COALESCE(MAX(CASE WHEN emd_code = '50110660' THEN store_count END), 0) AS store_count,
           ROUND(AVG(store_count), 1) AS jeju_avg
    FROM district_counts
)
SELECT ROUND(store_count / NULLIF(jeju_avg, 0), 1) AS density_ratio
FROM counts;
