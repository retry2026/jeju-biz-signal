package kr.jeju.agent.metric;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.YearMonth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class IndustryTourismMetricTest {

    private Connection connection;
    private JdbcClient jdbc;
    private IndustryTourismMetric metric;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DriverManager.getConnection("jdbc:duckdb:");
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource(connection, true);
        jdbc = JdbcClient.create(dataSource);
        jdbc.sql("""
                CREATE TABLE test_industry_spend (
                    ym VARCHAR,
                    industry VARCHAR,
                    user_type VARCHAR,
                    user_count BIGINT,
                    txn_count BIGINT,
                    amount BIGINT
                )
                """).update();
        jdbc.sql("""
                CREATE VIEW v_industry_spend AS
                SELECT ym, industry, user_type, user_count, txn_count, amount
                FROM test_industry_spend
                """).update();

        insertMonth("2024-12", 1_000, 10);
        for (int month = 1; month <= 12; month++) {
            insertMonth(YearMonth.of(2025, month).toString(), month * 1_000L, 20);
        }

        metric = new IndustryTourismMetric(jdbc);
    }

    @AfterEach
    void tearDown() throws SQLException {
        connection.close();
    }

    @Test
    void 결제액_비중은_법인을_제외하고_외국인_국가별_비중을_계산한다() {
        IndustryTourismResult result = metric.industryTourism("CAFE", 12);

        assertThat(result.residentShare()).isEqualTo(50.0);
        assertThat(result.domesticShare()).isEqualTo(30.0);
        assertThat(result.foreignShare()).isEqualTo(20.0);
        assertThat(result.touristShare()).isEqualTo(50.0);
        assertThat(result.foreignByCountry())
                .containsEntry("CHINA", 60.0)
                .containsEntry("JAPAN", 40.0);
    }

    @Test
    void 계절_지수는_기간_내_월별_평균을_100으로_계산한다() {
        IndustryTourismResult result = metric.industryTourism("CAFE", 12);

        assertThat(result.seasonIndex()).containsExactly(
                new MonthlyPoint("2025-01", 16.7),
                new MonthlyPoint("2025-02", 31.8),
                new MonthlyPoint("2025-03", 47.0),
                new MonthlyPoint("2025-04", 62.1),
                new MonthlyPoint("2025-05", 77.3),
                new MonthlyPoint("2025-06", 92.4),
                new MonthlyPoint("2025-07", 107.6),
                new MonthlyPoint("2025-08", 122.7),
                new MonthlyPoint("2025-09", 137.9),
                new MonthlyPoint("2025-10", 153.0),
                new MonthlyPoint("2025-11", 168.2),
                new MonthlyPoint("2025-12", 183.3)
        );
    }

    @Test
    void 최신월_건당_금액의_전년_동월_대비_증감률을_계산한다() {
        IndustryTourismResult result = metric.industryTourism("CAFE", 12);

        assertThat(result.ticketYoy()).isEqualTo(98.4);
    }

    @Test
    void 존재하지_않는_업종_코드는_예외로_처리한다() {
        assertThatThrownBy(() -> metric.industryTourism("UNKNOWN", 12))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void insertMonth(String ym, long consumerAmount, long transactionDivisor) {
        insertRow(ym, "RESIDENT", consumerAmount * 50 / 100, consumerAmount * 50 / 100 / transactionDivisor);
        insertRow(ym, "DOMESTIC", consumerAmount * 30 / 100, consumerAmount * 30 / 100 / transactionDivisor);
        insertRow(ym, "CHINA", consumerAmount * 12 / 100, consumerAmount * 12 / 100 / transactionDivisor);
        insertRow(ym, "JAPAN", consumerAmount * 8 / 100, consumerAmount * 8 / 100 / transactionDivisor);
        insertRow(ym, "CORP", 100, 10);
    }

    private void insertRow(String ym, String userType, long amount, long txnCount) {
        jdbc.sql("""
                INSERT INTO test_industry_spend (ym, industry, user_type, user_count, txn_count, amount)
                VALUES (:ym, :industry, :userType, :userCount, :txnCount, :amount)
                """)
                .param("ym", ym)
                .param("industry", "CAFE")
                .param("userType", userType)
                .param("userCount", txnCount)
                .param("txnCount", txnCount)
                .param("amount", amount)
                .update();
    }
}
