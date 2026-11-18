package kr.jeju.agent.web;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import kr.jeju.agent.web.dto.AreaDto;
import kr.jeju.agent.web.dto.DataAsOfDto;
import kr.jeju.agent.web.dto.IndustryDto;
import kr.jeju.agent.web.dto.MetaResponse;

/**
 * GET /api/meta. 지역, 업종, 예시 질문, 데이터 기준일을 ①의 참조 테이블과 뷰에서 읽는다.
 * 데이터가 아직 적재되지 않았으면 빈 목록과 null을 돌려준다 (앱은 계속 뜬다).
 */
@RestController
public class MetaController {

    private static final Logger log = LoggerFactory.getLogger(MetaController.class);

    static final Set<String> INDUSTRY_CODES = Set.of("CAFE", "KOREAN", "CHINESE", "JAPANESE", "WESTERN", "PUB", "FASTFOOD");

    /** S1은 CSV에 날짜 열이 없어 원본 스냅샷 날짜를 그대로 쓴다 (01_raw.sql 주석). */
    private static final String STORE_AS_OF = "2025-09-18";

    /** 어떤 업종을 골라도 맞는 문장만 쓴다. 업종 이름을 넣으면 다른 업종을 고른 사람에게 엉뚱한 예시가 된다. */
    private static final Map<String, List<String>> EXAMPLES = Map.of(
            "STARTUP", List.of(
                    "이 동네에 가게를 차려도 괜찮을까요?",
                    "손님은 도민이 많나요, 관광객이 많나요?",
                    "이 업종은 계절을 많이 타나요?"),
            "EXISTING", List.of(
                    "겨울만 되면 매출이 줄어요. 정상인가요?",
                    "작년보다 매출이 줄었는데 우리 가게 문제인가요?",
                    "요즘 관광객이 줄었나요?"));

    private final JdbcClient jdbc;

    public MetaController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/api/meta")
    public MetaResponse meta() {
        return new MetaResponse(areas(), industries(), EXAMPLES, dataAsOf());
    }

    Set<String> areaCodes() {
        return areas().stream().map(AreaDto::code).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<AreaDto> areas() {
        return query(() -> jdbc.sql("""
                SELECT DISTINCT emd_code, emd_name, city FROM ref_area ORDER BY city, emd_name""")
                .query((rs, i) -> new AreaDto(rs.getString(1), rs.getString(2), rs.getString(3)))
                .list(), List.of());
    }

    private List<IndustryDto> industries() {
        return query(() -> jdbc.sql("""
                SELECT industry, MIN(name_ko) FROM ref_industry GROUP BY industry
                ORDER BY list_position(['CAFE','KOREAN','CHINESE','JAPANESE','WESTERN','PUB','FASTFOOD'], industry)""")
                .query((rs, i) -> new IndustryDto(rs.getString(1), rs.getString(2)))
                .list(), List.of());
    }

    private DataAsOfDto dataAsOf() {
        return new DataAsOfDto(STORE_AS_OF,
                query(() -> jdbc.sql("SELECT MAX(ym) FROM v_area_spend").query(String.class).single(), null),
                query(() -> jdbc.sql("SELECT MAX(ym) FROM v_industry_spend").query(String.class).single(), null),
                query(() -> jdbc.sql("SELECT CAST(MAX(dt) AS VARCHAR) FROM v_arrivals").query(String.class).single(), null));
    }

    private static <T> T query(java.util.function.Supplier<T> q, T fallback) {
        try {
            return q.get();
        } catch (DataAccessException e) {
            log.warn("메타 조회 실패 (데이터 미적재?): {}", e.getMessage());
            return fallback;
        }
    }
}
