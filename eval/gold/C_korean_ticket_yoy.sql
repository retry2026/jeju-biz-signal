WITH latest AS (
    SELECT MAX(ym) AS latest_ym
    FROM v_industry_spend
    WHERE industry = 'KOREAN'
),
monthly AS (
    SELECT ym,
           SUM(amount) AS total_amount,
           SUM(txn_count) AS total_txn_count
    FROM v_industry_spend
    WHERE industry = 'KOREAN'
      AND ym IN (
          (SELECT latest_ym FROM latest),
          STRFTIME(
              CAST((SELECT latest_ym FROM latest) || '-01' AS DATE) - INTERVAL '1 year',
              '%Y-%m'
          )
      )
    GROUP BY ym
),
current_ticket AS (
    SELECT amount.total_amount,
           amount.total_txn_count
    FROM monthly AS amount
    CROSS JOIN latest
    WHERE amount.ym = latest.latest_ym
),
previous_ticket AS (
    SELECT amount.total_amount,
           amount.total_txn_count
    FROM monthly AS amount
    CROSS JOIN latest
    WHERE amount.ym = STRFTIME(
        CAST(latest.latest_ym || '-01' AS DATE) - INTERVAL '1 year',
        '%Y-%m'
    )
)
SELECT (
    SELECT CASE
               WHEN current_ticket.total_txn_count <> 0
                AND previous_ticket.total_txn_count <> 0
                AND previous_ticket.total_amount <> 0
               THEN ROUND(
                   (
                       CAST(current_ticket.total_amount AS DOUBLE) / current_ticket.total_txn_count
                       / (CAST(previous_ticket.total_amount AS DOUBLE) / previous_ticket.total_txn_count)
                       - 1
                   ) * 100,
                   1
               )
           END
    FROM current_ticket
    CROSS JOIN previous_ticket
) AS korean_ticket_yoy;
