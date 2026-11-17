package kr.jeju.agent.metric;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class CompetitionMetricTest {

    private Connection connection;
    private JdbcClient jdbc;
    private CompetitionMetric metric;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DriverManager.getConnection("jdbc:duckdb:");
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource(connection, true);
        jdbc = JdbcClient.create(dataSource);
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

        insertStore("a1", "카페 A1", "50110001", "동 A", 126.5, 33.502);
        insertStore("a2", "카페 A2", "50110001", "동 A", 126.5045, 33.502);
        insertStore("a3", "카페 A3", "50110001", "동 A", 126.4955, 33.502);
        insertStore("a4", "카페 A4", "50110001", "동 A", 126.5, 33.494);
        insertStore("b1", "카페 B1", "50110002", "동 B", 126.5, 33.7);
        insertStore("b2", "카페 B2", "50110002", "동 B", 126.5, 33.7);

        metric = new CompetitionMetric(jdbc);
    }

    @AfterEach
    void tearDown() throws SQLException {
        connection.close();
    }

    @Test
    void 동별_점포수와_제주_평균_및_밀집도를_계산한다() {
        CompetitionResult result = metric.competition("50110001", "CAFE", null);

        assertThat(result.storeCount()).isEqualTo(4);
        assertThat(result.jejuAvg()).isEqualTo(3.0);
        assertThat(result.densityRatio()).isEqualTo(1.3);
        assertThat(result.radiusM()).isNull();
        assertThat(result.radiusCount()).isNull();
    }

    @Test
    void 하버사인_거리로_반경_500미터_안의_점포를_센다() {
        CompetitionResult result = metric.competition("50110001", "CAFE", 500);

        assertThat(result.radiusM()).isEqualTo(500);
        assertThat(result.radiusCount()).isEqualTo(3);
    }

    @Test
    void 존재하지_않는_동_코드는_예외로_처리한다() {
        assertThatThrownBy(() -> metric.competition("99999999", "CAFE", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 동_중심_좌표를_구할_수_없으면_반경_점포수를_null로_반환한다() {
        jdbc.sql("""
                INSERT INTO test_store (store_id, store_name, emd_code, emd_name, industry, lon, lat)
                VALUES (:storeId, :storeName, :emdCode, :emdName, :industry, NULL, NULL)
                """)
                .param("storeId", "pub1")
                .param("storeName", "주점")
                .param("emdCode", "50110003")
                .param("emdName", "동 C")
                .param("industry", "PUB")
                .update();

        CompetitionResult result = metric.competition("50110003", "PUB", 500);

        assertThat(result.radiusCount()).isNull();
    }

    private void insertStore(
            String storeId,
            String storeName,
            String emdCode,
            String emdName,
            double lon,
            double lat
    ) {
        jdbc.sql("""
                INSERT INTO test_store (store_id, store_name, emd_code, emd_name, industry, lon, lat)
                VALUES (:storeId, :storeName, :emdCode, :emdName, :industry, :lon, :lat)
                """)
                .param("storeId", storeId)
                .param("storeName", storeName)
                .param("emdCode", emdCode)
                .param("emdName", emdName)
                .param("industry", "CAFE")
                .param("lon", lon)
                .param("lat", lat)
                .update();
    }
}
