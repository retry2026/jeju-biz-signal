WITH latest AS (
    SELECT MAX(dt) AS latest_dt
    FROM v_arrivals
),
totals AS (
    SELECT SUM(CASE
                   WHEN arrivals.dt BETWEEN latest.latest_dt - INTERVAL '27 days'
                                        AND latest.latest_dt
                   THEN arrivals.domestic_cnt
               END) AS current_total,
           SUM(CASE
                   WHEN arrivals.dt BETWEEN latest.latest_dt - INTERVAL '391 days'
                                        AND latest.latest_dt - INTERVAL '364 days'
                   THEN arrivals.domestic_cnt
               END) AS previous_total,
           COUNT(DISTINCT CASE
                   WHEN arrivals.dt BETWEEN latest.latest_dt - INTERVAL '27 days'
                                        AND latest.latest_dt
                   THEN arrivals.dt
               END) AS current_days,
           COUNT(DISTINCT CASE
                   WHEN arrivals.dt BETWEEN latest.latest_dt - INTERVAL '391 days'
                                        AND latest.latest_dt - INTERVAL '364 days'
                   THEN arrivals.dt
               END) AS previous_days
    FROM v_arrivals AS arrivals
    CROSS JOIN latest
)
SELECT CASE
           WHEN current_days = 28 AND previous_days = 28 AND previous_total <> 0
           THEN ROUND((CAST(current_total AS DOUBLE) / previous_total - 1) * 100, 1)
       END AS domestic_arrivals_yoy
FROM totals;
