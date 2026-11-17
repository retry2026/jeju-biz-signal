WITH latest AS (
    SELECT MAX(ym) AS latest_ym
    FROM v_area_spend
    WHERE emd_code = '50110660'
      AND category = 'FOOD'
),
bounds AS (
    SELECT latest_ym,
           CAST(latest_ym || '-01' AS DATE) AS latest_date,
           CASE WHEN EXISTS (
               SELECT 1
               FROM v_area_spend AS sample
               WHERE sample.emd_code = '50110660'
                 AND sample.category = 'FOOD'
                 AND sample.low_sample = TRUE
                 AND CAST(sample.ym || '-01' AS DATE)
                     BETWEEN CAST(latest_ym || '-01' AS DATE) - INTERVAL '11 months'
                         AND CAST(latest_ym || '-01' AS DATE)
           ) THEN 24 ELSE 12 END AS effective_months
    FROM latest
),
grouped AS (
    SELECT ym,
           customer_type,
           SUM(amount) AS amount,
           BOOL_OR(low_sample) AS low_sample
    FROM v_area_spend
    WHERE emd_code = '50110660'
      AND category = 'FOOD'
    GROUP BY ym, customer_type
),
valid_period AS (
    SELECT grouped.*
    FROM grouped
    CROSS JOIN bounds
    WHERE CAST(grouped.ym || '-01' AS DATE)
              BETWEEN bounds.latest_date - (bounds.effective_months - 1) * INTERVAL '1 month'
                  AND bounds.latest_date
      AND grouped.low_sample = FALSE
)
SELECT ROUND(
           100.0 * SUM(CASE WHEN customer_type = 'RESIDENT' THEN amount ELSE 0 END)
               / NULLIF(SUM(amount), 0),
           1
       ) AS resident_share
FROM valid_period;
