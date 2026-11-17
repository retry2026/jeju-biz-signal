package kr.jeju.agent.agent;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import kr.jeju.agent.metric.AreaCustomerMetric;
import kr.jeju.agent.metric.CompetitionMetric;
import kr.jeju.agent.metric.IndustryTourismMetric;

/**
 * 에이전트 테스트용 메모리 DuckDB. ②의 뷰 4개(API명세 4장)와 같은 모양으로 작은 데이터를 넣고
 * ②의 실제 지표 클래스를 그대로 만든다. 애월읍(AEWOL)에 카페 5곳, 24개월 외식 결제, 입도객 8주.
 */
final class AgentTestDb implements AutoCloseable {

    static final String AEWOL = "50110250";
    static final String OTHER = "50110660";

    private final Connection connection;
    final JdbcClient jdbc;
    final CompetitionMetric competition;
    final AreaCustomerMetric areaCustomers;
    final IndustryTourismMetric industryTourism;

    AgentTestDb() throws SQLException {
        connection = DriverManager.getConnection("jdbc:duckdb:");
        jdbc = JdbcClient.create(new SingleConnectionDataSource(connection, true));
        createViews();
        insertData();
        competition = new CompetitionMetric(jdbc);
        areaCustomers = new AreaCustomerMetric(jdbc);
        industryTourism = new IndustryTourismMetric(jdbc);
    }

    private void createViews() {
        jdbc.sql("""
                CREATE TABLE v_store (store_id VARCHAR, store_name VARCHAR, emd_code VARCHAR, emd_name VARCHAR,
                    industry VARCHAR, lon DOUBLE, lat DOUBLE)""").update();
        jdbc.sql("""
                CREATE TABLE v_area_spend (ym VARCHAR, emd_code VARCHAR, emd_name VARCHAR, customer_type VARCHAR,
                    category VARCHAR, txn_count BIGINT, amount BIGINT, low_sample BOOLEAN)""").update();
        jdbc.sql("""
                CREATE TABLE v_industry_spend (ym VARCHAR, industry VARCHAR, user_type VARCHAR,
                    user_count BIGINT, txn_count BIGINT, amount BIGINT)""").update();
        jdbc.sql("CREATE TABLE v_arrivals (dt DATE, domestic_cnt INTEGER, foreign_cnt INTEGER, total_cnt INTEGER)").update();
    }

    private void insertData() {
        for (int i = 1; i <= 5; i++) {
            jdbc.sql("INSERT INTO v_store VALUES (:id, :name, :emd, '애월읍', 'CAFE', 126.33, 33.46)")
                    .param("id", "s" + i).param("name", "카페 " + i).param("emd", AEWOL).update();
        }
        jdbc.sql("INSERT INTO v_store VALUES ('o1', '카페 노형', :emd, '노형동', 'CAFE', 126.48, 33.48)")
                .param("emd", OTHER).update();

        // 24개월: 여름(7~8월) 관광객 결제가 겨울의 2배. 도민 30%, 내국인 관광객 60%, 외국인 10% 근처.
        YearMonth latest = YearMonth.of(2026, 9);
        for (int back = 23; back >= 0; back--) {
            YearMonth ym = latest.minusMonths(back);
            long season = (ym.getMonthValue() == 7 || ym.getMonthValue() == 8) ? 2 : 1;
            long yearFactor = ym.getYear() == 2026 ? 9 : 10; // 2026년은 작년보다 10% 적음
            insertSpend(ym, AEWOL, "애월읍", "RESIDENT", 30_000_000L * yearFactor / 10, 2_000);
            insertSpend(ym, AEWOL, "애월읍", "DOMESTIC", 60_000_000L * season * yearFactor / 10, 3_000 * season);
            insertSpend(ym, AEWOL, "애월읍", "FOREIGN", 10_000_000L * season * yearFactor / 10, 500 * season);
            insertSpend(ym, OTHER, "노형동", "RESIDENT", 70_000_000L, 5_000);
            insertSpend(ym, OTHER, "노형동", "DOMESTIC", 25_000_000L, 1_200);
            insertSpend(ym, OTHER, "노형동", "FOREIGN", 5_000_000L, 200);
        }

        for (int back = 23; back >= 0; back--) {
            YearMonth ym = YearMonth.of(2025, 6).minusMonths(back);
            long base = 1_000_000_000L + ym.getMonthValue() * 50_000_000L;
            insertIndustry(ym, "RESIDENT", base * 40 / 100, 40_000);
            insertIndustry(ym, "DOMESTIC", base * 45 / 100, 30_000);
            insertIndustry(ym, "CHINA", base * 8 / 100, 4_000);
            insertIndustry(ym, "JAPAN", base * 4 / 100, 2_000);
            insertIndustry(ym, "SEA", base * 3 / 100, 1_500);
            insertIndustry(ym, "CORP", 10_000_000L, 500);
        }

        LocalDate last = LocalDate.of(2026, 11, 12);
        for (int d = 27; d >= 0; d--) {
            insertArrival(last.minusDays(d), 31_000, 9_000);
            insertArrival(last.minusDays(d).minusDays(364), 32_000, 8_000);
        }
    }

    private void insertSpend(YearMonth ym, String emd, String name, String type, long amount, long txn) {
        jdbc.sql("INSERT INTO v_area_spend VALUES (:ym, :emd, :name, :type, 'FOOD', :txn, :amount, false)")
                .param("ym", ym.toString()).param("emd", emd).param("name", name).param("type", type)
                .param("txn", txn).param("amount", amount).update();
    }

    private void insertIndustry(YearMonth ym, String userType, long amount, long txn) {
        jdbc.sql("INSERT INTO v_industry_spend VALUES (:ym, 'CAFE', :type, :txn, :txn, :amount)")
                .param("ym", ym.toString()).param("type", userType).param("txn", txn).param("amount", amount).update();
    }

    private void insertArrival(LocalDate dt, int domestic, int foreign) {
        jdbc.sql("INSERT INTO v_arrivals VALUES (CAST(:dt AS DATE), :d, :f, :t)")
                .param("dt", dt.toString()).param("d", domestic).param("f", foreign).param("t", domestic + foreign).update();
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }
}
