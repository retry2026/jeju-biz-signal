package kr.jeju.agent.metric;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class AreaCustomerMetric {

    private static final String AREA_EXISTS_SQL = """
            SELECT COUNT(*)
            FROM v_area_spend
            WHERE emd_code = :area
            """;

    private static final String LATEST_FOOD_MONTH_SQL = """
            SELECT MAX(ym)
            FROM v_area_spend
            WHERE emd_code = :area AND category = 'FOOD'
            """;

    private static final String LOW_SAMPLE_COUNT_SQL = """
            SELECT COUNT(*)
            FROM v_area_spend
            WHERE emd_code = :area
              AND category = 'FOOD'
              AND ym >= :startYm
              AND ym <= :endYm
              AND low_sample = TRUE
            """;

    private static final String AREA_SPEND_ROWS_SQL = """
            SELECT ym,
                   emd_code,
                   customer_type,
                   COALESCE(SUM(txn_count), 0) AS txn_count,
                   COALESCE(SUM(amount), 0) AS amount,
                   MAX(CASE WHEN low_sample THEN 1 ELSE 0 END) > 0 AS low_sample
            FROM v_area_spend
            WHERE category = 'FOOD'
              AND ym >= :startYm
              AND ym <= :endYm
            GROUP BY ym, emd_code, customer_type
            ORDER BY ym, emd_code, customer_type
            """;

    private static final String STORE_COUNT_SQL = """
            SELECT COUNT(*)
            FROM v_store
            WHERE emd_code = :area
            """;

    private static final String SEPTEMBER_TXN_SQL = """
            SELECT COUNT(*) AS row_count, COALESCE(SUM(txn_count), 0) AS txn_count
            FROM v_area_spend
            WHERE emd_code = :area
              AND category = 'FOOD'
              AND ym = '2025-09'
              AND low_sample = FALSE
            """;

    private static final String LATEST_ARRIVAL_DATE_SQL = """
            SELECT MAX(dt)
            FROM v_arrivals
            """;

    private static final String ARRIVAL_ROWS_SQL = """
            SELECT dt, domestic_cnt, foreign_cnt, total_cnt
            FROM v_arrivals
            WHERE (dt >= CAST(:thisStart AS DATE) AND dt <= CAST(:thisEnd AS DATE))
               OR (dt >= CAST(:lastStart AS DATE) AND dt <= CAST(:lastEnd AS DATE))
            ORDER BY dt
            """;

    private static final List<String> CUSTOMER_TYPES = List.of("RESIDENT", "DOMESTIC", "FOREIGN");
    private static final int ARRIVAL_DAYS = 28;
    private static final int YEAR_OVER_YEAR_DAY_OFFSET = 364;

    private final JdbcClient jdbc;

    public AreaCustomerMetric(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public AreaCustomerResult areaCustomers(String area, int months) {
        if (area == null || area.isBlank()) {
            throw new IllegalArgumentException("area 코드가 필요합니다.");
        }
        if (months <= 1) {
            throw new IllegalArgumentException("months는 1보다 커야 합니다.");
        }

        Long areaRowCount = jdbc.sql(AREA_EXISTS_SQL)
                .param("area", area)
                .query(Long.class)
                .single();
        if (areaRowCount == 0) {
            throw new IllegalArgumentException("존재하지 않는 동 코드입니다: " + area);
        }

        String latestYm = jdbc.sql(LATEST_FOOD_MONTH_SQL)
                .param("area", area)
                .query(String.class)
                .single();
        if (latestYm == null || latestYm.isBlank()) {
            throw new IllegalArgumentException("동의 음식점 결제 데이터가 없습니다: " + area);
        }

        YearMonth latestMonth;
        try {
            latestMonth = YearMonth.parse(latestYm);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("결제 데이터의 ym 형식이 올바르지 않습니다: " + latestYm, ex);
        }

        YearMonth requestedStart = latestMonth.minusMonths(months - 1L);
        boolean lowSample = months == 12 && hasLowSample(area, requestedStart, latestMonth);
        int effectiveMonths = lowSample ? 24 : months;
        YearMonth periodStart = latestMonth.minusMonths(effectiveMonths - 1L);
        YearMonth seasonalStart = latestMonth.minusMonths(11);
        YearMonth previousYearMonth = latestMonth.minusYears(1);
        YearMonth queryStart = min(periodStart, min(seasonalStart, previousYearMonth));
        String period = periodStart + " ~ " + latestMonth;

        Map<AreaMonthKey, Map<String, SpendRow>> spendByAreaMonth =
                loadSpend(queryStart, latestMonth);
        AreaSpendTotals areaTotals = areaTotals(area, periodStart, latestMonth, spendByAreaMonth);
        double jejuAvgTouristShare = jejuAvgTouristShare(periodStart, latestMonth, spendByAreaMonth);
        double seasonalRange = seasonalRange(area, latestMonth, spendByAreaMonth);
        Double yoyChange = yoyChange(area, periodStart, effectiveMonths, latestMonth, spendByAreaMonth);
        Double txnPerStore = txnPerStore(area);
        List<MonthlyPoint> residentMonthly = residentMonthly(area, periodStart, effectiveMonths, spendByAreaMonth);
        List<MonthlyPoint> touristMonthly = touristMonthly(area, periodStart, effectiveMonths, spendByAreaMonth);
        List<MonthlyPoint> jejuAvgMonthly = jejuAvgMonthly(periodStart, effectiveMonths, spendByAreaMonth);
        TouristFlow touristFlow = touristFlow();

        MetricMeta meta = new MetricMeta(
                "제주관광빅데이터플랫폼 지역별 소비",
                period,
                "AREA",
                lowSample,
                lowSample ? "최근 12개월 표본 부족으로 24개월 조회" : null
        );
        return new AreaCustomerResult(
                area,
                period,
                percent(areaTotals.residentAmount(), areaTotals.totalAmount()),
                percent(areaTotals.domesticAmount(), areaTotals.totalAmount()),
                percent(areaTotals.foreignAmount(), areaTotals.totalAmount()),
                percent(areaTotals.touristAmount(), areaTotals.totalAmount()),
                jejuAvgTouristShare,
                ticket(areaTotals.residentAmount(), areaTotals.residentTxnCount()),
                ticket(areaTotals.touristAmount(), areaTotals.touristTxnCount()),
                seasonalRange,
                yoyChange,
                txnPerStore,
                residentMonthly,
                touristMonthly,
                jejuAvgMonthly,
                touristFlow,
                meta
        );
    }

    private boolean hasLowSample(String area, YearMonth start, YearMonth end) {
        Long lowSampleCount = jdbc.sql(LOW_SAMPLE_COUNT_SQL)
                .param("area", area)
                .param("startYm", start.toString())
                .param("endYm", end.toString())
                .query(Long.class)
                .single();
        return lowSampleCount > 0;
    }

    private Map<AreaMonthKey, Map<String, SpendRow>> loadSpend(YearMonth start, YearMonth end) {
        List<SpendRow> rows = jdbc.sql(AREA_SPEND_ROWS_SQL)
                .param("startYm", start.toString())
                .param("endYm", end.toString())
                .query((rs, rowNum) -> new SpendRow(
                        rs.getString("ym"),
                        rs.getString("emd_code"),
                        rs.getString("customer_type"),
                        rs.getLong("txn_count"),
                        rs.getLong("amount"),
                        rs.getBoolean("low_sample")
                ))
                .list();

        Map<AreaMonthKey, Map<String, SpendRow>> spendByAreaMonth = new HashMap<>();
        for (SpendRow row : rows) {
            AreaMonthKey key = new AreaMonthKey(row.ym(), row.emdCode());
            spendByAreaMonth.computeIfAbsent(key, ignored -> new HashMap<>())
                    .put(row.customerType(), row);
        }
        return spendByAreaMonth;
    }

    private AreaSpendTotals areaTotals(
            String area,
            YearMonth start,
            YearMonth end,
            Map<AreaMonthKey, Map<String, SpendRow>> spendByAreaMonth
    ) {
        long totalAmount = 0;
        long residentAmount = 0;
        long domesticAmount = 0;
        long foreignAmount = 0;
        long residentTxnCount = 0;
        long touristAmount = 0;
        long touristTxnCount = 0;

        for (YearMonth month = start; !month.isAfter(end); month = month.plusMonths(1)) {
            Map<String, SpendRow> rows = rowsFor(spendByAreaMonth, month, area);
            for (SpendRow row : rows.values()) {
                if (row.lowSample()) {
                    continue;
                }
                totalAmount = Math.addExact(totalAmount, row.amount());
                if ("RESIDENT".equals(row.customerType())) {
                    residentAmount = Math.addExact(residentAmount, row.amount());
                    residentTxnCount = Math.addExact(residentTxnCount, row.txnCount());
                } else if ("DOMESTIC".equals(row.customerType())) {
                    domesticAmount = Math.addExact(domesticAmount, row.amount());
                    touristAmount = Math.addExact(touristAmount, row.amount());
                    touristTxnCount = Math.addExact(touristTxnCount, row.txnCount());
                } else if ("FOREIGN".equals(row.customerType())) {
                    foreignAmount = Math.addExact(foreignAmount, row.amount());
                    touristAmount = Math.addExact(touristAmount, row.amount());
                    touristTxnCount = Math.addExact(touristTxnCount, row.txnCount());
                }
            }
        }
        if (totalAmount == 0) {
            throw new IllegalStateException("표본이 유효한 음식점 결제액이 없습니다: " + area);
        }
        return new AreaSpendTotals(
                totalAmount,
                residentAmount,
                domesticAmount,
                foreignAmount,
                residentTxnCount,
                touristAmount,
                touristTxnCount
        );
    }

    private double jejuAvgTouristShare(
            YearMonth start,
            YearMonth end,
            Map<AreaMonthKey, Map<String, SpendRow>> spendByAreaMonth
    ) {
        Map<String, long[]> totalsByArea = new HashMap<>();
        for (YearMonth month = start; !month.isAfter(end); month = month.plusMonths(1)) {
            for (Map.Entry<AreaMonthKey, Map<String, SpendRow>> entry : spendByAreaMonth.entrySet()) {
                if (!entry.getKey().ym().equals(month.toString()) || !isComplete(entry.getValue())) {
                    continue;
                }
                long[] totals = totalsByArea.computeIfAbsent(entry.getKey().emdCode(), ignored -> new long[2]);
                Map<String, SpendRow> rows = entry.getValue();
                totals[0] = Math.addExact(totals[0], row(rows, "RESIDENT").amount()
                        + row(rows, "DOMESTIC").amount() + row(rows, "FOREIGN").amount());
                totals[1] = Math.addExact(totals[1], row(rows, "DOMESTIC").amount() + row(rows, "FOREIGN").amount());
            }
        }

        double shareTotal = 0;
        int areaCount = 0;
        for (long[] totals : totalsByArea.values()) {
            if (totals[0] > 0) {
                shareTotal += totals[1] / (double) totals[0] * 100;
                areaCount++;
            }
        }
        if (areaCount == 0) {
            throw new IllegalStateException("제주 평균 관광객 비중을 계산할 유효 데이터가 없습니다.");
        }
        return roundToOneDecimal(shareTotal / areaCount);
    }

    private double seasonalRange(
            String area,
            YearMonth latestMonth,
            Map<AreaMonthKey, Map<String, SpendRow>> spendByAreaMonth
    ) {
        long minimum = Long.MAX_VALUE;
        long maximum = Long.MIN_VALUE;
        int validMonthCount = 0;
        YearMonth start = latestMonth.minusMonths(11);
        for (YearMonth month = start; !month.isAfter(latestMonth); month = month.plusMonths(1)) {
            Map<String, SpendRow> rows = rowsFor(spendByAreaMonth, month, area);
            if (!isComplete(rows)) {
                continue;
            }
            long total = totalAmount(rows);
            minimum = Math.min(minimum, total);
            maximum = Math.max(maximum, total);
            validMonthCount++;
        }
        if (validMonthCount == 0 || minimum <= 0) {
            throw new IllegalStateException("계절 변동폭을 계산할 유효 월별 결제액이 없습니다: " + area);
        }
        return roundToOneDecimal(maximum / (double) minimum);
    }

    private Double yoyChange(
            String area,
            YearMonth periodStart,
            int months,
            YearMonth latestMonth,
            Map<AreaMonthKey, Map<String, SpendRow>> spendByAreaMonth
    ) {
        YearMonth currentMonth = latestMonth;
        for (int i = 0; i < months; i++, currentMonth = currentMonth.minusMonths(1)) {
            Map<String, SpendRow> currentRows = rowsFor(spendByAreaMonth, currentMonth, area);
            if (!isComplete(currentRows)) {
                continue;
            }
            YearMonth previousMonth = currentMonth.minusYears(1);
            if (previousMonth.isBefore(periodStart.minusYears(1))) {
                return null;
            }
            Map<String, SpendRow> previousRows = rowsFor(spendByAreaMonth, previousMonth, area);
            if (!isComplete(previousRows)) {
                return null;
            }
            long previousAmount = totalAmount(previousRows);
            if (previousAmount == 0) {
                return null;
            }
            return roundToOneDecimal((totalAmount(currentRows) / (double) previousAmount - 1) * 100);
        }
        return null;
    }

    private Double txnPerStore(String area) {
        SeptemberTransactions september = jdbc.sql(SEPTEMBER_TXN_SQL)
                .param("area", area)
                .query((rs, rowNum) -> new SeptemberTransactions(
                        rs.getLong("row_count"),
                        rs.getLong("txn_count")
                ))
                .single();
        if (september.rowCount() == 0) {
            return null;
        }
        Long storeCount = jdbc.sql(STORE_COUNT_SQL)
                .param("area", area)
                .query(Long.class)
                .single();
        if (storeCount == 0) {
            return null;
        }
        return roundToOneDecimal(september.txnCount() / (double) storeCount);
    }

    private List<MonthlyPoint> residentMonthly(
            String area,
            YearMonth start,
            int months,
            Map<AreaMonthKey, Map<String, SpendRow>> spendByAreaMonth
    ) {
        List<MonthlyPoint> points = new ArrayList<>(months);
        for (int i = 0; i < months; i++) {
            YearMonth month = start.plusMonths(i);
            SpendRow row = row(rowsFor(spendByAreaMonth, month, area), "RESIDENT");
            Double value = row == null || row.lowSample() ? null : (double) row.amount();
            points.add(new MonthlyPoint(month.toString(), value));
        }
        return List.copyOf(points);
    }

    private List<MonthlyPoint> touristMonthly(
            String area,
            YearMonth start,
            int months,
            Map<AreaMonthKey, Map<String, SpendRow>> spendByAreaMonth
    ) {
        List<MonthlyPoint> points = new ArrayList<>(months);
        for (int i = 0; i < months; i++) {
            YearMonth month = start.plusMonths(i);
            Map<String, SpendRow> rows = rowsFor(spendByAreaMonth, month, area);
            SpendRow domestic = row(rows, "DOMESTIC");
            SpendRow foreign = row(rows, "FOREIGN");
            Double value = domestic == null || foreign == null
                    || domestic.lowSample() || foreign.lowSample()
                    ? null
                    : (double) Math.addExact(domestic.amount(), foreign.amount());
            points.add(new MonthlyPoint(month.toString(), value));
        }
        return List.copyOf(points);
    }

    private List<MonthlyPoint> jejuAvgMonthly(
            YearMonth start,
            int months,
            Map<AreaMonthKey, Map<String, SpendRow>> spendByAreaMonth
    ) {
        List<MonthlyPoint> points = new ArrayList<>(months);
        for (int i = 0; i < months; i++) {
            YearMonth month = start.plusMonths(i);
            double total = 0;
            int areaCount = 0;
            for (Map.Entry<AreaMonthKey, Map<String, SpendRow>> entry : spendByAreaMonth.entrySet()) {
                if (entry.getKey().ym().equals(month.toString()) && isComplete(entry.getValue())) {
                    total += totalAmount(entry.getValue());
                    areaCount++;
                }
            }
            Double average = areaCount == 0 ? null : total / areaCount;
            points.add(new MonthlyPoint(month.toString(), average));
        }
        return List.copyOf(points);
    }

    private TouristFlow touristFlow() {
        List<LocalDate> latestDates = jdbc.sql(LATEST_ARRIVAL_DATE_SQL)
                .query((rs, rowNum) -> {
                    Date date = rs.getDate(1);
                    return date == null ? null : date.toLocalDate();
                })
                .list();
        if (latestDates.isEmpty() || latestDates.get(0) == null) {
            throw new IllegalStateException("입도객 데이터가 없습니다.");
        }

        LocalDate latestDate = latestDates.get(0);
        LocalDate thisStart = latestDate.minusDays(ARRIVAL_DAYS - 1L);
        LocalDate lastEnd = latestDate.minusDays(YEAR_OVER_YEAR_DAY_OFFSET);
        LocalDate lastStart = lastEnd.minusDays(ARRIVAL_DAYS - 1L);
        List<ArrivalRow> arrivalRows = jdbc.sql(ARRIVAL_ROWS_SQL)
                .param("thisStart", thisStart.toString())
                .param("thisEnd", latestDate.toString())
                .param("lastStart", lastStart.toString())
                .param("lastEnd", lastEnd.toString())
                .query((rs, rowNum) -> arrivalRow(rs))
                .list();
        Map<LocalDate, ArrivalRow> arrivalsByDate = new LinkedHashMap<>();
        for (ArrivalRow row : arrivalRows) {
            if (arrivalsByDate.put(row.dt(), row) != null) {
                throw new IllegalStateException("입도객 날짜가 중복되었습니다: " + row.dt());
            }
        }

        List<SeriesPoint> thisYear = arrivalSeries(thisStart, latestDate, arrivalsByDate);
        List<SeriesPoint> lastYear = arrivalSeries(lastStart, lastEnd, arrivalsByDate);
        long thisDomestic = arrivalTotal(thisStart, latestDate, arrivalsByDate, true);
        long thisForeign = arrivalTotal(thisStart, latestDate, arrivalsByDate, false);
        long lastDomestic = arrivalTotal(lastStart, lastEnd, arrivalsByDate, true);
        long lastForeign = arrivalTotal(lastStart, lastEnd, arrivalsByDate, false);
        if (lastDomestic == 0 || lastForeign == 0) {
            throw new IllegalStateException("전년 동기간 입도객 합계가 0입니다.");
        }

        return new TouristFlow(
                thisStart + " ~ " + latestDate,
                roundToOneDecimal((thisDomestic / (double) lastDomestic - 1) * 100),
                roundToOneDecimal((thisForeign / (double) lastForeign - 1) * 100),
                thisYear,
                lastYear
        );
    }

    private static ArrivalRow arrivalRow(ResultSet rs) throws SQLException {
        Date sqlDate = rs.getDate("dt");
        int domestic = requiredInt(rs, "domestic_cnt");
        int foreign = requiredInt(rs, "foreign_cnt");
        int total = requiredInt(rs, "total_cnt");
        if (sqlDate == null) {
            throw new IllegalStateException("입도객 날짜가 없습니다.");
        }
        return new ArrivalRow(sqlDate.toLocalDate(), domestic, foreign, total);
    }

    private static int requiredInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        if (rs.wasNull()) {
            throw new IllegalStateException("입도객 데이터가 없습니다: " + column);
        }
        return value;
    }

    private static List<SeriesPoint> arrivalSeries(
            LocalDate start,
            LocalDate end,
            Map<LocalDate, ArrivalRow> arrivalsByDate
    ) {
        List<SeriesPoint> points = new ArrayList<>(ARRIVAL_DAYS);
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            ArrivalRow row = arrivalsByDate.get(date);
            if (row == null) {
                throw new IllegalStateException("입도객 날짜 데이터가 누락되었습니다: " + date);
            }
            points.add(new SeriesPoint(date.toString(), (double) row.totalCount()));
        }
        return List.copyOf(points);
    }

    private static long arrivalTotal(
            LocalDate start,
            LocalDate end,
            Map<LocalDate, ArrivalRow> arrivalsByDate,
            boolean domestic
    ) {
        long total = 0;
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            ArrivalRow row = arrivalsByDate.get(date);
            if (row == null) {
                throw new IllegalStateException("입도객 날짜 데이터가 누락되었습니다: " + date);
            }
            total = Math.addExact(total, domestic ? row.domesticCount() : row.foreignCount());
        }
        return total;
    }

    private static Map<String, SpendRow> rowsFor(
            Map<AreaMonthKey, Map<String, SpendRow>> spendByAreaMonth,
            YearMonth month,
            String area
    ) {
        return spendByAreaMonth.getOrDefault(new AreaMonthKey(month.toString(), area), Map.of());
    }

    private static SpendRow row(Map<String, SpendRow> rows, String customerType) {
        return rows.get(customerType);
    }

    private static boolean isComplete(Map<String, SpendRow> rows) {
        for (String customerType : CUSTOMER_TYPES) {
            SpendRow row = rows.get(customerType);
            if (row == null || row.lowSample()) {
                return false;
            }
        }
        return true;
    }

    private static long totalAmount(Map<String, SpendRow> rows) {
        long total = 0;
        for (String customerType : CUSTOMER_TYPES) {
            total = Math.addExact(total, rows.get(customerType).amount());
        }
        return total;
    }

    private static Long ticket(long amount, long txnCount) {
        return txnCount == 0 ? null : Math.round(amount / (double) txnCount);
    }

    private static double percent(long amount, long total) {
        if (total == 0) {
            throw new IllegalStateException("결제액 합계가 0이어서 비중을 계산할 수 없습니다.");
        }
        return roundToOneDecimal(amount / (double) total * 100);
    }

    private static double roundToOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static YearMonth min(YearMonth first, YearMonth second) {
        return first.isBefore(second) ? first : second;
    }

    private record SpendRow(
            String ym,
            String emdCode,
            String customerType,
            long txnCount,
            long amount,
            boolean lowSample
    ) {}

    private record AreaMonthKey(String ym, String emdCode) {}

    private record AreaSpendTotals(
            long totalAmount,
            long residentAmount,
            long domesticAmount,
            long foreignAmount,
            long residentTxnCount,
            long touristAmount,
            long touristTxnCount
    ) {}

    private record SeptemberTransactions(long rowCount, long txnCount) {}

    private record ArrivalRow(LocalDate dt, int domesticCount, int foreignCount, int totalCount) {}
}
