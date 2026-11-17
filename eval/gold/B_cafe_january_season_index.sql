WITH latest AS (
    SELECT MAX(ym) AS latest_ym
    FROM v_industry_spend
    WHERE industry = 'CAFE'
),
monthly AS (
    SELECT spend.ym,
           SUM(spend.amount) AS amount
    FROM v_industry_spend AS spend
    CROSS JOIN latest
    WHERE spend.industry = 'CAFE'
      AND CAST(spend.ym || '-01' AS DATE)
              BETWEEN CAST(latest.latest_ym || '-01' AS DATE) - INTERVAL '11 months'
                  AND CAST(latest.latest_ym || '-01' AS DATE)
    GROUP BY spend.ym
),
average AS (
    SELECT AVG(amount) AS monthly_average
    FROM monthly
)
SELECT (
    SELECT ROUND(monthly.amount / NULLIF(average.monthly_average, 0) * 100.0, 1)
    FROM monthly
    CROSS JOIN average
    WHERE STRFTIME(CAST(monthly.ym || '-01' AS DATE), '%m') = '01'
) AS january_season_index;
