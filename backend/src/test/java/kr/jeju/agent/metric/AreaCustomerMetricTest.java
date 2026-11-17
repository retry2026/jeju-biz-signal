package kr.jeju.agent.metric;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class AreaCustomerMetricTest {

    private static final String AREA = "50110001";
    private static final int[] MONTH_UNITS = {50, 60, 65, 70, 75, 80, 85, 90, 95, 100, 80, 55};

    private Connection connection;
    private JdbcClient jdbc;
    private AreaCustomerMetric metric;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DriverManager.getConnection("jdbc:duckdb:");
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource(connection, true);
        jdbc = JdbcClient.create(dataSource);
        createAreaSpendView();
        createStoreView();
        createArrivalsView();
        insertAreaSpendMonths();
        insertStores();
        insertArrivals();

        metric = new AreaCustomerMetric(jdbc);
    }

    @AfterEach
    void tearDown() throws SQLException {
        connection.close();
    }

    @Test
    void 손님_비중과_건당_결제액을_계산한다() {
        AreaCustomerResult result = metric.areaCustomers(AREA, 12);

        assertThat(result.residentShare()).isEqualTo(30.0);
        assertThat(result.domesticShare()).isEqualTo(60.0);
        assertThat(result.foreignShare()).isEqualTo(10.0);
        assertThat(result.touristShare()).isEqualTo(70.0);
        assertThat(result.residentTicket()).isEqualTo(10_000L);
        assertThat(result.touristTicket()).isEqualTo(28_000L);
    }

    @Test
    void 계절_변동폭과_전년_동월_변화율_및_점포당_건수를_계산한다() {
        AreaCustomerResult result = metric.areaCustomers(AREA, 12);

        assertThat(result.seasonalRange()).isEqualTo(2.0);
        assertThat(result.yoyChange()).isEqualTo(10.0);
        assertThat(result.txnPerStore()).isEqualTo(209.0);
    }

    @Test
    void 음식점업_표본_부족이면_24개월로_확장하고_해당_월을_null로_표시한다() {
        jdbc.sql("""
                UPDATE test_area_spend
                SET low_sample = TRUE
                WHERE emd_code = :area
                  AND ym = :ym
                  AND category = 'FOOD'
                  AND customer_type = 'RESIDENT'
                """)
                .param("area", AREA)
                .param("ym", "2025-06")
                .update();

        AreaCustomerResult result = metric.areaCustomers(AREA, 12);
        Optional<MonthlyPoint> flaggedMonth = result.residentMonthly().stream()
                .filter(point -> point.ym().equals("2025-06"))
                .findFirst();

        assertThat(result.period()).isEqualTo("2024-01 ~ 2025-12");
        assertThat(result.residentMonthly()).hasSize(24);
        assertThat(result.meta().lowSample()).isTrue();
        assertThat(flaggedMonth).contains(new MonthlyPoint("2025-06", null));
    }

    @Test
    void 다른_카테고리의_표본_부족은_기간_확장에_영향을_주지_않는다() {
        insertSpend("2025-06", "RETAIL", "RESIDENT", 1_000, 1, true);

        AreaCustomerResult result = metric.areaCustomers(AREA, 12);

        assertThat(result.period()).isEqualTo("2025-01 ~ 2025-12");
        assertThat(result.meta().lowSample()).isFalse();
    }

    @Test
    void 입도객_흐름은_최신일과_364일_간격으로_비교한다() {
        AreaCustomerResult result = metric.areaCustomers(AREA, 12);
        TouristFlow flow = result.touristFlow();

        assertThat(flow.period()).isEqualTo("2026-10-16 ~ 2026-11-12");
        assertThat(flow.domesticYoy()).isEqualTo(10.0);
        assertThat(flow.foreignYoy()).isEqualTo(20.0);
        assertThat(flow.thisYear()).hasSize(28);
        assertThat(flow.thisYear().get(0)).isEqualTo(new SeriesPoint("2026-10-16", 350.0));
        assertThat(flow.thisYear().get(27)).isEqualTo(new SeriesPoint("2026-11-12", 350.0));
        assertThat(flow.lastYear()).hasSize(28);
        assertThat(flow.lastYear().get(0)).isEqualTo(new SeriesPoint("2025-10-17", 300.0));
        assertThat(flow.lastYear().get(27)).isEqualTo(new SeriesPoint("2025-11-13", 300.0));
    }

    @Test
    void 없는_동_코드와_잘못된_months는_예외로_처리한다() {
        assertThatThrownBy(() -> metric.areaCustomers("99999999", 12))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> metric.areaCustomers(AREA, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> metric.areaCustomers(AREA, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void createAreaSpendView() {
        jdbc.sql("""
                CREATE TABLE test_area_spend (
                    ym VARCHAR,
                    emd_code VARCHAR,
                    emd_name VARCHAR,
                    customer_type VARCHAR,
                    category VARCHAR,
                    txn_count BIGINT,
                    amount BIGINT,
                    low_sample BOOLEAN
                )
                """).update();
        jdbc.sql("""
                CREATE VIEW v_area_spend AS
                SELECT ym, emd_code, emd_name, customer_type, category, txn_count, amount, low_sample
                FROM test_area_spend
                """).update();
    }

    private void createStoreView() {
        jdbc.sql("""
                CREATE TABLE test_store (
                    store_id VARCHAR,
                    store_name VARCHAR,
                    emd_code VARCHAR,
                    emd_name VARCHAR,
                    industry VARCHAR,
                    lon DOUBLE,
                    lat DOUBLE
                )
                """).update();
        jdbc.sql("""
                CREATE VIEW v_store AS
                SELECT store_id, store_name, emd_code, emd_name, industry, lon, lat
                FROM test_store
                """).update();
    }

    private void createArrivalsView() {
        jdbc.sql("""
                CREATE TABLE test_arrivals (
                    dt DATE,
                    domestic_cnt INTEGER,
                    foreign_cnt INTEGER
                )
                """).update();
        jdbc.sql("""
                CREATE VIEW v_arrivals AS
                SELECT dt, domestic_cnt, foreign_cnt, domestic_cnt + foreign_cnt AS total_cnt
                FROM test_arrivals
                """).update();
    }

    private void insertAreaSpendMonths() {
        for (int month = 1; month <= 12; month++) {
            long units = month == 12 ? 50 : MONTH_UNITS[month - 1];
            insertMonth(YearMonth.of(2024, month).toString(), units);
            insertMonth(YearMonth.of(2025, month).toString(), MONTH_UNITS[month - 1]);
        }
    }

    private void insertMonth(String ym, long units) {
        insertSpend(ym, "FOOD", "RESIDENT", units * 60_000, units * 6, false);
        insertSpend(ym, "FOOD", "DOMESTIC", units * 120_000, units * 4, false);
        insertSpend(ym, "FOOD", "FOREIGN", units * 20_000, units, false);
    }

    private void insertSpend(
            String ym,
            String category,
            String customerType,
            long amount,
            long txnCount,
            boolean lowSample
    ) {
        jdbc.sql("""
                INSERT INTO test_area_spend (
                    ym, emd_code, emd_name, customer_type, category, txn_count, amount, low_sample
                )
                VALUES (:ym, :area, '동 A', :customerType, :category, :txnCount, :amount, :lowSample)
                """)
                .param("ym", ym)
                .param("area", AREA)
                .param("customerType", customerType)
                .param("category", category)
                .param("txnCount", txnCount)
                .param("amount", amount)
                .param("lowSample", lowSample)
                .update();
    }

    private void insertStores() {
        for (int index = 1; index <= 5; index++) {
            jdbc.sql("""
                    INSERT INTO test_store (store_id, store_name, emd_code, emd_name, industry, lon, lat)
                    VALUES (:storeId, :storeName, :area, '동 A', 'CAFE', 126.5, 33.5)
                    """)
                    .param("storeId", "store-" + index)
                    .param("storeName", "카페 " + index)
                    .param("area", AREA)
                    .update();
        }
    }

    private void insertArrivals() {
        LocalDate latestDate = LocalDate.of(2026, 11, 12);
        for (int day = 27; day >= 0; day--) {
            LocalDate currentDate = latestDate.minusDays(day);
            insertArrival(currentDate, 110, 240);
            insertArrival(currentDate.minusDays(364), 100, 200);
        }
    }

    private void insertArrival(LocalDate date, int domesticCount, int foreignCount) {
        jdbc.sql("""
                INSERT INTO test_arrivals (dt, domestic_cnt, foreign_cnt)
                VALUES (CAST(:dt AS DATE), :domesticCount, :foreignCount)
                """)
                .param("dt", date.toString())
                .param("domesticCount", domesticCount)
                .param("foreignCount", foreignCount)
                .update();
    }
}
