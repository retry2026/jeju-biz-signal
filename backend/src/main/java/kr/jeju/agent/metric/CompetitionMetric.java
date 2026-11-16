package kr.jeju.agent.metric;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class CompetitionMetric {

    private static final String AREA_EXISTS_SQL = """
            SELECT COUNT(*)
            FROM v_store
            WHERE emd_code = :area
            """;

    private static final String COUNTS_SQL = """
            WITH area_codes AS (
                SELECT DISTINCT emd_code
                FROM v_store
            ),
            industry_counts AS (
                SELECT emd_code, COUNT(*) AS store_count
                FROM v_store
                WHERE industry = :industry
                GROUP BY emd_code
            ),
            district_counts AS (
                SELECT area_codes.emd_code,
                       COALESCE(industry_counts.store_count, 0) AS store_count
                FROM area_codes
                LEFT JOIN industry_counts USING (emd_code)
            )
            SELECT COALESCE(MAX(CASE WHEN emd_code = :area THEN store_count END), 0) AS store_count,
                   ROUND(AVG(store_count), 1) AS jeju_avg
            FROM district_counts
            """;

    private static final String RADIUS_CENTER_COUNT_SQL = """
            SELECT COUNT(*)
            FROM v_store
            WHERE emd_code = :area AND lon IS NOT NULL AND lat IS NOT NULL
            """;

    private static final String RADIUS_COUNT_SQL = """
            WITH area_center AS (
                SELECT AVG(lon) AS center_lon, AVG(lat) AS center_lat
                FROM v_store
                WHERE emd_code = :area AND lon IS NOT NULL AND lat IS NOT NULL
            )
            SELECT COUNT(*)
            FROM v_store AS stores
            CROSS JOIN area_center
            WHERE stores.industry = :industry
              AND stores.lon IS NOT NULL
              AND stores.lat IS NOT NULL
              AND 2 * 6371000.0 * ASIN(SQRT(LEAST(1.0,
                    POWER(SIN(RADIANS(stores.lat - area_center.center_lat) / 2), 2)
                    + COS(RADIANS(area_center.center_lat)) * COS(RADIANS(stores.lat))
                    * POWER(SIN(RADIANS(stores.lon - area_center.center_lon) / 2), 2)
              ))) <= :radiusM
            """;

    private final JdbcClient jdbc;

    public CompetitionMetric(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public CompetitionResult competition(String area, String industry, Integer radiusM) {
        if (area == null || area.isBlank()) {
            throw new IllegalArgumentException("area 코드가 필요합니다.");
        }
        if (industry == null || industry.isBlank()) {
            throw new IllegalArgumentException("industry 코드가 필요합니다.");
        }
        if (radiusM != null && radiusM <= 0) {
            throw new IllegalArgumentException("radiusM은 양수여야 합니다.");
        }

        Long areaStoreCount = jdbc.sql(AREA_EXISTS_SQL)
                .param("area", area)
                .query(Long.class)
                .single();
        if (areaStoreCount == 0) {
            throw new IllegalArgumentException("존재하지 않는 동 코드입니다: " + area);
        }

        CompetitionCounts counts = jdbc.sql(COUNTS_SQL)
                .param("area", area)
                .param("industry", industry)
                .query((rs, rowNum) -> new CompetitionCounts(
                        rs.getLong("store_count"),
                        rs.getDouble("jeju_avg")
                ))
                .single();

        if (counts.jejuAvg() == 0.0) {
            throw new IllegalArgumentException("해당 업종의 점포 데이터가 없습니다: " + industry);
        }

        int storeCount = Math.toIntExact(counts.storeCount());
        Integer radiusCount = null;
        if (radiusM != null) {
            Long centerCoordinateCount = jdbc.sql(RADIUS_CENTER_COUNT_SQL)
                    .param("area", area)
                    .query(Long.class)
                    .single();
            if (centerCoordinateCount > 0) {
                radiusCount = Math.toIntExact(jdbc.sql(RADIUS_COUNT_SQL)
                        .param("area", area)
                        .param("industry", industry)
                        .param("radiusM", radiusM)
                        .query(Long.class)
                        .single());
            }
        }
        double densityRatio = roundToOneDecimal(storeCount / counts.jejuAvg());
        MetricMeta meta = new MetricMeta(
                "제주데이터허브 제주 상가(상권) 정보",
                null,
                radiusM == null ? "AREA" : "RADIUS",
                false,
                radiusM == null ? null : "동 중심은 v_store 점포 좌표 평균으로 추정"
        );

        return new CompetitionResult(
                area,
                industry,
                storeCount,
                counts.jejuAvg(),
                densityRatio,
                radiusM,
                radiusM == null ? null : radiusCount,
                meta
        );
    }

    private static double roundToOneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private record CompetitionCounts(long storeCount, double jejuAvg) {}
}
