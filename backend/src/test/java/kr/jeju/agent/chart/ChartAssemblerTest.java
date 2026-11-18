package kr.jeju.agent.chart;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import kr.jeju.agent.metric.AreaCustomerResult;
import kr.jeju.agent.metric.CompetitionResult;
import kr.jeju.agent.metric.IndustryTourismResult;
import kr.jeju.agent.metric.MetricMeta;
import kr.jeju.agent.metric.MonthlyPoint;
import kr.jeju.agent.metric.SeriesPoint;
import kr.jeju.agent.metric.TouristFlow;

class ChartAssemblerTest {

    private static final String AREA_SOURCE = "제주관광빅데이터플랫폼 지역별 소비";
    private static final String COMPETITION_SOURCE = "제주데이터허브 제주 상가(상권) 정보";
    private static final String INDUSTRY_SOURCE = "제주데이터허브 업종별 성별 카드 이용 정보";
    private static final String AREA_PERIOD = "2025-01 ~ 2025-02";

    private final ChartAssembler assembler = new ChartAssembler();

    @Test
    void 일곱_차트를_명세된_데이터와_순서로_조립한다() {
        List<String> chartIds = List.of(
                "customerMix",
                "areaMonthly",
                "ticketSize",
                "touristFlow",
                "industrySeason",
                "competition",
                "myStoreVsArea"
        );

        List<Chart> charts = chartIds.stream()
                .map(chartId -> assembler.build(List.of(chartId), completeSnapshot()).get(0))
                .toList();

        assertThat(charts).extracting(Chart::id).containsExactlyElementsOf(chartIds);

        Chart customerMix = chart(charts, "customerMix");
        assertThat(customerMix.type()).isEqualTo("doughnut");
        assertThat(customerMix.labels()).containsExactly("도민", "내국인 관광객", "외국인 관광객");
        assertThat(customerMix.datasets()).containsExactly(
                new ChartDataset("결제 비중", List.of(30.0, 60.0, 10.0), null)
        );
        assertThat(customerMix.unit()).isEqualTo("%");
        assertThat(customerMix.source()).isEqualTo(AREA_SOURCE + " (" + AREA_PERIOD + ")");
        assertThat(customerMix.scope()).isEqualTo("AREA");

        Chart areaMonthly = chart(charts, "areaMonthly");
        assertThat(areaMonthly.type()).isEqualTo("line");
        assertThat(areaMonthly.labels()).containsExactly("2025-01", "2025-02");
        assertThat(areaMonthly.datasets()).containsExactly(
                new ChartDataset("도민", List.of(1_000.0, 1_200.0), null),
                new ChartDataset("관광객", List.of(2_000.0, 3_000.0), null),
                new ChartDataset("제주 동 평균", java.util.Arrays.asList(800.0, null), true)
        );
        assertThat(areaMonthly.unit()).isEqualTo("원");
        assertThat(areaMonthly.source()).isEqualTo(AREA_SOURCE + " (" + AREA_PERIOD + ")");
        assertThat(areaMonthly.scope()).isEqualTo("AREA");

        Chart ticketSize = chart(charts, "ticketSize");
        assertThat(ticketSize.type()).isEqualTo("bar");
        assertThat(ticketSize.labels()).containsExactly("도민", "관광객");
        assertThat(ticketSize.datasets()).containsExactly(
                new ChartDataset("건당 결제액", List.of(10_000.0, 28_000.0), null)
        );
        assertThat(ticketSize.unit()).isEqualTo("원");
        assertThat(ticketSize.source()).isEqualTo(AREA_SOURCE + " (" + AREA_PERIOD + ")");
        assertThat(ticketSize.scope()).isEqualTo("AREA");

        Chart touristFlow = chart(charts, "touristFlow");
        assertThat(touristFlow.type()).isEqualTo("line");
        assertThat(touristFlow.labels()).containsExactly("2026-10-01", "2026-10-02");
        assertThat(touristFlow.datasets()).containsExactly(
                new ChartDataset("올해", List.of(100.0, 110.0), null),
                new ChartDataset("작년", List.of(90.0, 100.0), null)
        );
        assertThat(touristFlow.unit()).isEqualTo("명");
        assertThat(touristFlow.source()).isEqualTo(AREA_SOURCE + " (2026-10-01 ~ 2026-10-28)");
        assertThat(touristFlow.scope()).isEqualTo("AREA");

        Chart industrySeason = chart(charts, "industrySeason");
        assertThat(industrySeason.type()).isEqualTo("line");
        assertThat(industrySeason.labels()).containsExactly("2025-01", "2025-02");
        assertThat(industrySeason.datasets()).containsExactly(
                new ChartDataset("계절 지수", List.of(90.0, 110.0), null)
        );
        assertThat(industrySeason.unit()).isEqualTo("지수");
        assertThat(industrySeason.source()).isEqualTo(INDUSTRY_SOURCE + " (2025-01 ~ 2025-02)");
        assertThat(industrySeason.scope()).isEqualTo("JEJU");

        Chart competition = chart(charts, "competition");
        assertThat(competition.type()).isEqualTo("bar");
        assertThat(competition.labels()).containsExactly("동네", "제주 평균");
        assertThat(competition.datasets()).containsExactly(
                new ChartDataset("점포 수", List.of(4.0, 3.0), null)
        );
        assertThat(competition.unit()).isEqualTo("개");
        assertThat(competition.source()).isEqualTo(COMPETITION_SOURCE);
        assertThat(competition.scope()).isEqualTo("AREA");

        Chart myStore = chart(charts, "myStoreVsArea");
        assertThat(myStore.type()).isEqualTo("bar");
        assertThat(myStore.labels()).containsExactly("내 가게", "동네 외식");
        assertThat(myStore.datasets()).containsExactly(
                new ChartDataset("전년 대비 변화", List.of(20.0, 10.0), null)
        );
        assertThat(myStore.unit()).isEqualTo("%");
        assertThat(myStore.source()).isEqualTo(AREA_SOURCE + " (" + AREA_PERIOD + ")");
        assertThat(myStore.scope()).isEqualTo("AREA");
    }

    @Test
    void 필수_결과나_내_가게_비교자료가_없으면_해당_차트를_건너뛴다() {
        EvidenceSnapshot partial = new EvidenceSnapshot(
                null,
                areaCustomers(),
                null,
                new MyStoreInput(List.of(new MonthlySale("2025-01", 100_000)))
        );

        assertThat(assembler.build(
                List.of("competition", "industrySeason"),
                partial
        )).isEmpty();
        assertThat(assembler.build(
                List.of("myStoreVsArea"),
                new EvidenceSnapshot(null, areaCustomers(), null, null)
        )).isEmpty();
    }

    @Test
    void 작년_같은_달_매출이_없으면_입력한_월매출을_달_순서대로_보여준다() {
        EvidenceSnapshot snapshot = new EvidenceSnapshot(null, areaCustomers(), null, new MyStoreInput(List.of(
                new MonthlySale("2026-05", 2_000_000),
                new MonthlySale("2025-02", 2_000_000),
                new MonthlySale("2025-03", 4_000_000))));

        Chart chart = chart(assembler.build(List.of("myStoreVsArea"), snapshot), "myStoreVsArea");

        assertThat(chart.type()).isEqualTo("line");
        assertThat(chart.labels()).containsExactly("2025-02", "2025-03", "2026-05");
        assertThat(chart.datasets()).containsExactly(
                new ChartDataset("내 가게 월매출", List.of(2_000_000.0, 4_000_000.0, 2_000_000.0), null));
        assertThat(chart.unit()).isEqualTo("원");
    }

    @Test
    void 두_동네를_조회하면_손님_구성과_결제_차트를_동네_이름별로_나란히_그린다() {
        AreaCustomerResult a = areaCustomers();
        AreaCustomerResult b = new AreaCustomerResult("50110002", a.period(), 25.0, 65.0, 10.0, 75.0, 35.0,
                9_000L, 30_000L, 2.0, 5.0, 100.0, a.residentMonthly(), a.touristMonthly(), a.jejuAvgMonthly(),
                a.touristFlow(), a.meta());
        EvidenceSnapshot snapshot = new EvidenceSnapshot(null, b, null, null, List.of(
                new EvidenceSnapshot.NamedAreaCustomers("조천읍", a),
                new EvidenceSnapshot.NamedAreaCustomers("한림읍", b)));

        List<Chart> charts = assembler.build(List.of("customerMix", "ticketSize", "areaMonthly"), snapshot);

        Chart mix = chart(charts, "customerMix");
        assertThat(mix.type()).isEqualTo("bar");
        assertThat(mix.datasets()).containsExactly(
                new ChartDataset("조천읍", List.of(30.0, 60.0, 10.0), null),
                new ChartDataset("한림읍", List.of(25.0, 65.0, 10.0), null));
        assertThat(chart(charts, "ticketSize").datasets()).extracting(ChartDataset::label).containsExactly("조천읍", "한림읍");
        Chart monthly = chart(charts, "areaMonthly");
        assertThat(monthly.datasets().get(0)).isEqualTo(new ChartDataset("조천읍", List.of(3_000.0, 4_200.0), null));
    }

    @Test
    void 요청_순서를_유지하고_유효한_차트를_최대_세_개만_반환한다() {
        List<Chart> charts = assembler.build(
                List.of("touristFlow", "unknown", "competition", "customerMix", "areaMonthly", "industrySeason"),
                completeSnapshot()
        );

        assertThat(charts).extracting(Chart::id)
                .containsExactly("touristFlow", "competition", "customerMix")
                .hasSizeLessThanOrEqualTo(3);
    }

    private static Chart chart(List<Chart> charts, String id) {
        return charts.stream()
                .filter(item -> item.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void 반경을_물어도_경쟁_차트는_동네_점포_수라_범위는_AREA다() {
        CompetitionResult radius = new CompetitionResult("50110001", "CAFE", 4, 3.0, 1.3, 500, 2,
                new MetricMeta(COMPETITION_SOURCE, null, "RADIUS", false, null));

        Chart chart = assembler.build(List.of("competition"), new EvidenceSnapshot(radius, null, null, null)).get(0);

        assertThat(chart.scope()).isEqualTo("AREA");
        assertThat(chart.datasets().get(0).data()).containsExactly(4.0, 3.0);
    }

    private static EvidenceSnapshot completeSnapshot() {
        CompetitionResult competition = new CompetitionResult(
                "50110001",
                "CAFE",
                4,
                3.0,
                1.3,
                null,
                null,
                new MetricMeta(COMPETITION_SOURCE, null, "AREA", false, null)
        );
        IndustryTourismResult industry = new IndustryTourismResult(
                "CAFE",
                "2025-01 ~ 2025-02",
                50.0,
                30.0,
                20.0,
                50.0,
                java.util.Map.of("CHINA", 60.0, "JAPAN", 40.0),
                List.of(new MonthlyPoint("2025-01", 90.0), new MonthlyPoint("2025-02", 110.0)),
                5.0,
                new MetricMeta(INDUSTRY_SOURCE, "2025-01 ~ 2025-02", "JEJU", false, null)
        );
        List<MonthlySale> sales = List.of(
                new MonthlySale("2024-05", 200_000),
                new MonthlySale("2025-05", 250_000),
                new MonthlySale("2024-06", 300_000),
                new MonthlySale("2025-06", 360_000)
        );
        return new EvidenceSnapshot(
                competition,
                areaCustomers(),
                industry,
                new MyStoreInput(sales)
        );
    }

    private static AreaCustomerResult areaCustomers() {
        return new AreaCustomerResult(
                "50110001",
                AREA_PERIOD,
                30.0,
                60.0,
                10.0,
                70.0,
                35.0,
                10_000L,
                28_000L,
                2.0,
                10.0,
                209.0,
                List.of(new MonthlyPoint("2025-01", 1_000.0), new MonthlyPoint("2025-02", 1_200.0)),
                List.of(new MonthlyPoint("2025-01", 2_000.0), new MonthlyPoint("2025-02", 3_000.0)),
                java.util.Arrays.asList(
                        new MonthlyPoint("2025-01", 800.0),
                        new MonthlyPoint("2025-02", null)
                ),
                new TouristFlow(
                        "2026-10-01 ~ 2026-10-28",
                        10.0,
                        20.0,
                        List.of(new SeriesPoint("2026-10-01", 100.0), new SeriesPoint("2026-10-02", 110.0)),
                        List.of(new SeriesPoint("2025-10-02", 90.0), new SeriesPoint("2025-10-03", 100.0))
                ),
                new MetricMeta(AREA_SOURCE, AREA_PERIOD, "AREA", false, null)
        );
    }
}
