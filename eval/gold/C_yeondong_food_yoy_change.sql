WITH latest AS (
    SELECT MAX(ym) AS latest_ym
    FROM v_area_spend
    WHERE emd_name = '연동'
      AND category = 'FOOD'
),
bounds AS (
    SELECT latest_ym,
           CAST(latest_ym || '-01' AS DATE) AS latest_date,
           CASE WHEN EXISTS (
               SELECT 1
               FROM v_area_spend AS sample
               WHERE sample.emd_name = '연동'
                 AND sample.category = 'FOOD'
                 AND sample.low_sample = TRUE
                 AND CAST(sample.ym || '-01' AS DATE)
                     BETWEEN CAST(latest_ym || '-01' AS DATE) - INTERVAL '11 months'
                         AND CAST(latest_ym || '-01' AS DATE)
           ) THEN 24 ELSE 12 END AS effective_months
    FROM latest
),
grouped AS (
    SELECT spend.ym,
           spend.customer_type,
           SUM(spend.amount) AS amount,
           BOOL_OR(spend.low_sample) AS low_sample
    FROM v_area_spend AS spend
    CROSS JOIN bounds
    WHERE spend.emd_name = '연동'
      AND spend.category = 'FOOD'
      AND CAST(spend.ym || '-01' AS DATE)
              BETWEEN bounds.latest_date
                      - GREATEST(bounds.effective_months - 1, 12) * INTERVAL '1 month'
                  AND bounds.latest_date
    GROUP BY spend.ym, spend.customer_type
),
monthly AS (
    SELECT CAST(ym || '-01' AS DATE) AS month_date,
           SUM(amount) AS total_amount
    FROM grouped
    GROUP BY ym
    HAVING COUNT(DISTINCT CASE
               WHEN customer_type IN ('RESIDENT', 'DOMESTIC', 'FOREIGN')
                AND low_sample = FALSE THEN customer_type
           END) = 3
),
current_month AS (
    SELECT MAX(monthly.month_date) AS month_date
    FROM monthly
    CROSS JOIN bounds
    WHERE monthly.month_date
              BETWEEN bounds.latest_date - (bounds.effective_months - 1) * INTERVAL '1 month'
                  AND bounds.latest_date
)
SELECT (
    SELECT CASE
               WHEN previous.total_amount <> 0
               THEN ROUND(
                   (CAST(current.total_amount AS DOUBLE) / previous.total_amount - 1) * 100,
                   1
               )
           END
    FROM current_month
    JOIN monthly AS current ON current.month_date = current_month.month_date
    JOIN monthly AS previous
      ON previous.month_date = current_month.month_date - INTERVAL '1 year'
) AS area_food_yoy_change;
