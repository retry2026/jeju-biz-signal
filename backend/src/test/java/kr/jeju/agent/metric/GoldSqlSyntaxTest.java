package kr.jeju.agent.metric;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class GoldSqlSyntaxTest {

    @Test
    void goldSql은DuckDB에서단일값을반환한다() throws Exception {
        Path goldDirectory = Files.isDirectory(Path.of("eval", "gold"))
                ? Path.of("eval", "gold")
                : Path.of("..", "eval", "gold");
        List<Path> sqlFiles;
        try (Stream<Path> files = Files.list(goldDirectory)) {
            sqlFiles = files.filter(path -> path.toString().endsWith(".sql"))
                    .sorted()
                    .toList();
        }
        assertThat(sqlFiles).isNotEmpty();

        try (Connection connection = DriverManager.getConnection("jdbc:duckdb:");
             Statement statement = connection.createStatement()) {
            createViews(statement);
            for (Path sqlFile : sqlFiles) {
                try (ResultSet result = statement.executeQuery(Files.readString(sqlFile))) {
                    assertThat(result.getMetaData().getColumnCount())
                            .as(sqlFile.getFileName().toString())
                            .isEqualTo(1);
                    assertThat(result.next()).as(sqlFile.getFileName().toString()).isTrue();
                    assertThat(result.next()).as(sqlFile.getFileName().toString()).isFalse();
                }
            }
        }
    }

    private static void createViews(Statement statement) throws Exception {
        statement.execute("""
                CREATE VIEW v_store AS
                SELECT CAST(NULL AS VARCHAR) AS store_id,
                       CAST(NULL AS VARCHAR) AS store_name,
                       CAST(NULL AS VARCHAR) AS emd_code,
                       CAST(NULL AS VARCHAR) AS emd_name,
                       CAST(NULL AS VARCHAR) AS industry,
                       CAST(NULL AS DOUBLE) AS lon,
                       CAST(NULL AS DOUBLE) AS lat
                WHERE FALSE
                """);
        statement.execute("""
                CREATE VIEW v_area_spend AS
                SELECT CAST(NULL AS VARCHAR) AS ym,
                       CAST(NULL AS VARCHAR) AS emd_code,
                       CAST(NULL AS VARCHAR) AS emd_name,
                       CAST(NULL AS VARCHAR) AS customer_type,
                       CAST(NULL AS VARCHAR) AS category,
                       CAST(NULL AS BIGINT) AS txn_count,
                       CAST(NULL AS BIGINT) AS amount,
                       CAST(NULL AS BOOLEAN) AS low_sample
                WHERE FALSE
                """);
        statement.execute("""
                CREATE VIEW v_industry_spend AS
                SELECT CAST(NULL AS VARCHAR) AS ym,
                       CAST(NULL AS VARCHAR) AS industry,
                       CAST(NULL AS VARCHAR) AS user_type,
                       CAST(NULL AS BIGINT) AS user_count,
                       CAST(NULL AS BIGINT) AS txn_count,
                       CAST(NULL AS BIGINT) AS amount
                WHERE FALSE
                """);
        statement.execute("""
                CREATE VIEW v_arrivals AS
                SELECT CAST(NULL AS DATE) AS dt,
                       CAST(NULL AS INTEGER) AS domestic_cnt,
                       CAST(NULL AS INTEGER) AS foreign_cnt,
                       CAST(NULL AS INTEGER) AS total_cnt
                WHERE FALSE
                """);
    }
}
