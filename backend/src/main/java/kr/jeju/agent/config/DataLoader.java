package kr.jeju.agent.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 앱이 시작될 때 data/sql/*.sql을 파일 이름 순서로 실행해 DuckDB 테이블과 뷰를 만든다.
 * SQL은 CREATE OR REPLACE로 써서 매번 다시 실행해도 같은 결과가 나오게 한다. (① 소유)
 *
 * SQL 안의 ${DATA_DIR}는 app.data-dir의 절대경로로 바꾼다.
 * read_csv의 상대경로는 실행 위치에 따라 달라지므로 절대경로로 고정한다.
 */
@Component
public class DataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataLoader.class);

    private static final String DATA_DIR_TOKEN = "${DATA_DIR}";

    private final JdbcTemplate jdbc;
    private final Path dataDir;
    private final Path sqlDir;

    public DataLoader(JdbcTemplate jdbc, @Value("${app.data-dir}") String dataDir) {
        this.jdbc = jdbc;
        this.dataDir = Path.of(dataDir).toAbsolutePath().normalize();
        this.sqlDir = this.dataDir.resolve("sql");
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (!Files.isDirectory(sqlDir)) {
            log.warn("SQL 폴더가 없어 데이터 적재를 건너뜁니다: {}", sqlDir.toAbsolutePath());
            return;
        }
        List<Path> files;
        try (Stream<Path> s = Files.list(sqlDir)) {
            files = s.filter(p -> p.toString().endsWith(".sql")).sorted().toList();
        }
        String dataDirPath = dataDir.toString().replace('\\', '/');
        for (Path file : files) {
            long start = System.currentTimeMillis();
            String sql = Files.readString(file, StandardCharsets.UTF_8).replace(DATA_DIR_TOKEN, dataDirPath);
            jdbc.execute(sql);
            log.info("SQL 실행 완료: {} ({} ms)", file.getFileName(), System.currentTimeMillis() - start);
        }
    }
}
