WITH latest AS (
    SELECT MAX(ym) AS latest_ym
    FROM v_area_spend
    WHERE emd_name = '애월읍'
      AND category = 'FOOD'
),
grouped AS (
    SELECT ym,
           customer_type,
           SUM(amount) AS amount,
           BOOL_OR(low_sample) AS low_sample
    FROM v_area_spend
    WHERE emd_name = '애월읍'
      AND category = 'FOOD'
    GROUP BY ym, customer_type
),
monthly AS (
    SELECT grouped.ym,
           SUM(CASE
                   WHEN customer_type IN ('RESIDENT', 'DOMESTIC', 'FOREIGN')
                    AND low_sample = FALSE THEN amount
                   ELSE 0
               END) AS total_amount
    FROM grouped
    CROSS JOIN latest
    WHERE CAST(grouped.ym || '-01' AS DATE)
              BETWEEN CAST(latest.latest_ym || '-01' AS DATE) - INTERVAL '11 months'
                  AND CAST(latest.latest_ym || '-01' AS DATE)
    GROUP BY grouped.ym
    HAVING COUNT(DISTINCT CASE
               WHEN customer_type IN ('RESIDENT', 'DOMESTIC', 'FOREIGN')
                AND low_sample = FALSE THEN customer_type
           END) = 3
)
SELECT ROUND(MAX(total_amount) / NULLIF(MIN(total_amount), 0), 1) AS seasonal_range
FROM monthly;
