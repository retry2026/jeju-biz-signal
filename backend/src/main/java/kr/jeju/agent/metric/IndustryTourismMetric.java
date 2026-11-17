package kr.jeju.agent.metric;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class IndustryTourismMetric {

    private static final String INDUSTRY_COUNT_SQL = """
            SELECT COUNT(*)
            FROM v_industry_spend
            WHERE industry = :industry
            """;

    private static final String LATEST_MONTH_SQL = """
            SELECT MAX(ym)
            FROM v_industry_spend
            WHERE industry = :industry
            """;

    private static final String PERIOD_AMOUNTS_SQL = """
            SELECT user_type, COALESCE(SUM(amount), 0) AS total_amount
            FROM v_industry_spend
            WHERE industry = :industry
              AND ym >= :startYm
              AND ym <= :endYm
            GROUP BY user_type
            """;

    private static final String MONTHLY_AMOUNTS_SQL = """
            SELECT ym, COALESCE(SUM(amount), 0) AS total_amount
            FROM v_industry_spend
            WHERE industry = :industry
              AND ym >= :startYm
              AND ym <= :endYm
            GROUP BY ym
            ORDER BY ym
            """;

    private static final String YEAR_OVER_YEAR_SQL = """
            SELECT ym,
                   COALESCE(SUM(amount), 0) AS total_amount,
                   COALESCE(SUM(txn_count), 0) AS total_txn_count
            FROM v_industry_spend
            WHERE industry = :industry
              AND (ym = :latestYm OR ym = :previousYm)
            GROUP BY ym
            """;

    private static final List<String> FOREIGN_COUNTRIES = List.of("CHINA", "JAPAN", "SEA", "OTHER");

    private final JdbcClient jdbc;

    public IndustryTourismMetric(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public IndustryTourismResult industryTourism(String industry, int months) {
        if (industry == null || industry.isBlank()) {
            throw new IllegalArgumentException("industry 코드가 필요합니다.");
        }
        if (months <= 0) {
            throw new IllegalArgumentException("months는 양수여야 합니다.");
        }

        Long industryRowCount = jdbc.sql(INDUSTRY_COUNT_SQL)
                .param("industry", industry)
                .query(Long.class)
                .single();
        if (industryRowCount == 0) {
            throw new IllegalArgumentException("존재하지 않는 업종 코드입니다: " + industry);
        }

        String latestYm = jdbc.sql(LATEST_MONTH_SQL)
                .param("industry", industry)
                .query(String.class)
                .single();
        if (latestYm == null || latestYm.isBlank()) {
            throw new IllegalArgumentException("업종 데이터에 기준 월이 없습니다: " + industry);
        }

        YearMonth latestMonth;
        try {
            latestMonth = YearMonth.parse(latestYm);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("업종 데이터의 ym 형식이 올바르지 않습니다: " + latestYm, ex);
        }
        YearMonth startMonth = latestMonth.minusMonths(months - 1L);
        String startYm = startMonth.toString();
        String period = startYm + " ~ " + latestYm;

        Map<String, Long> amountsByUserType = new LinkedHashMap<>();
        List<UserTypeAmount> userTypeAmounts = jdbc.sql(PERIOD_AMOUNTS_SQL)
                .param("industry", industry)
                .param("startYm", startYm)
                .param("endYm", latestYm.toString())
                .query((rs, rowNum) -> new UserTypeAmount(
                        rs.getString("user_type"),
                        rs.getLong("total_amount")
                ))
                .list();
        for (UserTypeAmount amount : userTypeAmounts) {
            amountsByUserType.put(amount.userType(), amount.amount());
        }

        boolean hasForeignRows = FOREIGN_COUNTRIES.stream().anyMatch(amountsByUserType::containsKey);
        if (!amountsByUserType.containsKey("RESIDENT")
                || !amountsByUserType.containsKey("DOMESTIC")
                || !hasForeignRows) {
            throw new IllegalStateException("비중 계산에 필요한 user_type 데이터가 없습니다: " + industry);
        }

        long nonCorporateTotal = 0;
        for (Map.Entry<String, Long> entry : amountsByUserType.entrySet()) {
            if (!"CORP".equals(entry.getKey())) {
                nonCorporateTotal = Math.addExact(nonCorporateTotal, entry.getValue());
            }
        }
        if (nonCorporateTotal == 0) {
            throw new IllegalStateException("법인을 제외한 결제액이 없어 비중을 계산할 수 없습니다: " + industry);
        }

        long foreignTotal = 0;
        for (String country : FOREIGN_COUNTRIES) {
            foreignTotal = Math.addExact(foreignTotal, amountsByUserType.getOrDefault(country, 0L));
        }

        double residentShare = percent(amountsByUserType.getOrDefault("RESIDENT", 0L), nonCorporateTotal);
        double domesticShare = percent(amountsByUserType.getOrDefault("DOMESTIC", 0L), nonCorporateTotal);
        double foreignShare = percent(foreignTotal, nonCorporateTotal);
        Map<String, Double> foreignByCountry = foreignShares(amountsByUserType, foreignTotal);
        List<MonthlyPoint> seasonIndex = seasonIndex(industry, startYm, latestYm.toString(), months);
        Double ticketYoy = ticketYoy(industry, latestMonth);

        MetricMeta meta = new MetricMeta(
                "제주데이터허브 업종별 성별 카드 이용 정보",
                period,
                "JEJU",
                false,
                null
        );
        return new IndustryTourismResult(
                industry,
                period,
                residentShare,
                domesticShare,
                foreignShare,
                foreignByCountry,
                seasonIndex,
                ticketYoy,
                meta
        );
    }

    private List<MonthlyPoint> seasonIndex(String industry, String startYm, String endYm, int months) {
        List<MonthlyAmount> monthlyAmounts = jdbc.sql(MONTHLY_AMOUNTS_SQL)
                .param("industry", industry)
                .param("startYm", startYm)
                .param("endYm", endYm)
                .query((rs, rowNum) -> new MonthlyAmount(
                        rs.getString("ym"),
                        rs.getLong("total_amount")
                ))
                .list();
        Map<String, Long> amountByMonth = new LinkedHashMap<>();
        for (MonthlyAmount amount : monthlyAmounts) {
            amountByMonth.put(amount.ym(), amount.amount());
        }

        double total = 0;
        for (long amount : amountByMonth.values()) {
            total += amount;
        }
        double average = amountByMonth.isEmpty() ? 0 : total / amountByMonth.size();

        List<MonthlyPoint> points = new ArrayList<>(months);
        YearMonth month = YearMonth.parse(startYm);
        for (int i = 0; i < months; i++) {
            String ym = month.plusMonths(i).toString();
            Long amount = amountByMonth.get(ym);
            Double index = amount == null || average == 0
                    ? null
                    : roundToOneDecimal(amount / average * 100.0);
            points.add(new MonthlyPoint(ym, index));
        }
        return List.copyOf(points);
    }

    private Double ticketYoy(String industry, YearMonth latestMonth) {
        String latestYm = latestMonth.toString();
        String previousYm = latestMonth.minusYears(1).toString();
        List<MonthlyTransactions> monthlyTransactions = jdbc.sql(YEAR_OVER_YEAR_SQL)
                .param("industry", industry)
                .param("latestYm", latestYm)
                .param("previousYm", previousYm)
                .query((rs, rowNum) -> new MonthlyTransactions(
                        rs.getString("ym"),
                        rs.getLong("total_amount"),
                        rs.getLong("total_txn_count")
                ))
                .list();
        Map<String, MonthlyTransactions> transactionsByMonth = new LinkedHashMap<>();
        for (MonthlyTransactions transactions : monthlyTransactions) {
            transactionsByMonth.put(transactions.ym(), transactions);
        }

        MonthlyTransactions current = transactionsByMonth.get(latestYm);
        MonthlyTransactions previous = transactionsByMonth.get(previousYm);
        if (current == null || previous == null
                || current.txnCount() == 0 || previous.txnCount() == 0) {
            return null;
        }

        double previousTicket = previous.amount() / (double) previous.txnCount();
        if (previousTicket == 0) {
            return null;
        }
        double currentTicket = current.amount() / (double) current.txnCount();
        return roundToOneDecimal((currentTicket / previousTicket - 1) * 100);
    }

    private static Map<String, Double> foreignShares(Map<String, Long> amountsByUserType, long foreignTotal) {
        Map<String, Double> shares = new LinkedHashMap<>();
        for (String country : FOREIGN_COUNTRIES) {
            Long amount = amountsByUserType.get(country);
            Double share = foreignTotal == 0 || amount == null
                    ? null
                    : percent(amount, foreignTotal);
            shares.put(country, share);
        }
        return Collections.unmodifiableMap(shares);
    }

    private static double percent(long amount, long total) {
        return roundToOneDecimal(amount / (double) total * 100);
    }

    private static double roundToOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private record UserTypeAmount(String userType, long amount) {}

    private record MonthlyAmount(String ym, long amount) {}

    private record MonthlyTransactions(String ym, long amount, long txnCount) {}
}
