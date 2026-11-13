package kr.jeju.agent.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 앱이 LLM 키 없이 뜨고, 시작 시 SQL 파일(여러 문장)이 실행되며,
 * 커넥션 풀에서 DuckDB 파일에 동시에 두 커넥션을 열 수 있는지 확인한다.
 */
@SpringBootTest
class DataLoaderTest {

    static Path dataDir;

    @DynamicPropertySource
    static void dataDir(DynamicPropertyRegistry registry) throws IOException {
        dataDir = Files.createTempDirectory("jeju-data");
        Path sqlDir = Files.createDirectories(dataDir.resolve("sql"));
        Files.copy(new ClassPathResource("test-data/sql/01_test.sql").getInputStream(), sqlDir.resolve("01_test.sql"));
        registry.add("app.data-dir", () -> dataDir.toString().replace('\\', '/'));
    }

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    DataSource dataSource;

    @Test
    void 시작할_때_SQL_파일의_여러_문장을_모두_실행한다() {
        Integer total = jdbc.queryForObject("SELECT total FROM v_sample", Integer.class);
        assertThat(total).isEqualTo(3);
    }

    @Test
    void 같은_DuckDB_파일에_커넥션_두_개를_동시에_연다() throws Exception {
        try (Connection a = dataSource.getConnection(); Connection b = dataSource.getConnection();
             ResultSet ra = a.createStatement().executeQuery("SELECT count(*) FROM t_sample");
             ResultSet rb = b.createStatement().executeQuery("SELECT count(*) FROM t_sample")) {
            ra.next();
            rb.next();
            assertThat(ra.getInt(1)).isEqualTo(2);
            assertThat(rb.getInt(1)).isEqualTo(2);
        }
    }
}
