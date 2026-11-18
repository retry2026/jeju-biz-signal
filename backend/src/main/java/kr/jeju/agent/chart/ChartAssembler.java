package kr.jeju.agent.chart;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import kr.jeju.agent.metric.AreaCustomerResult;
import kr.jeju.agent.metric.CompetitionResult;
import kr.jeju.agent.metric.IndustryTourismResult;
import kr.jeju.agent.metric.MetricMeta;
import kr.jeju.agent.metric.MonthlyPoint;
import kr.jeju.agent.metric.SeriesPoint;
import kr.jeju.agent.metric.TouristFlow;

public class ChartAssembler {

    public List<Chart> build(List<String> chartIds, EvidenceSnapshot results) {
        Objects.requireNonNull(chartIds, "chartIds");
        Objects.requireNonNull(results, "results");

        List<Chart> charts = new ArrayList<>();
        for (String chartId : chartIds) {
            if (charts.size() == 3) {
                break;
            }
            Chart chart = buildChart(chartId, results);
            if (chart != null) {
                charts.add(chart);
            }
        }
        return List.copyOf(charts);
    }

    private Chart buildChart(String chartId, EvidenceSnapshot results) {
        List<EvidenceSnapshot.NamedAreaCustomers> compared =
                results.comparedAreas() == null ? List.of() : results.comparedAreas();
        if (compared.size() >= 2) {
            // 두 동네를 조회했으면 마지막 동네 하나만 그리지 않고, 동네 이름을 범례로 나란히 그린다.
            Chart comparison = switch (chartId) {
                case "customerMix" -> customerMixComparison(compared);
                case "ticketSize" -> ticketSizeComparison(compared);
                case "areaMonthly" -> areaMonthlyComparison(compared);
                default -> null;
            };
            if (comparison != null) {
                return comparison;
            }
        }
        return switch (chartId) {
            case "customerMix" -> customerMix(results.areaCustomers());
            case "areaMonthly" -> areaMonthly(results.areaCustomers());
            case "ticketSize" -> ticketSize(results.areaCustomers());
            case "touristFlow" -> touristFlow(results.areaCustomers());
            case "industrySeason" -> industrySeason(results.industryTourism());
            case "competition" -> competition(results.competition());
            case "myStoreVsArea" -> myStoreVsArea(results.areaCustomers(), results.myStore());
            default -> null;
        };
    }

    private Chart customerMixComparison(List<EvidenceSnapshot.NamedAreaCustomers> areas) {
        AreaCustomerResult first = areas.get(0).result();
        return new Chart(
                "customerMix",
                "bar",
                "동네 손님 구성 비교",
                List.of("도민", "내국인 관광객", "외국인 관광객"),
                areas.stream().map(a -> new ChartDataset(a.name(),
                        List.of(a.result().residentShare(), a.result().domesticShare(), a.result().foreignShare()),
                        null)).toList(),
                "%",
                source(first.meta(), first.meta().period()),
                first.meta().scope()
        );
    }

    private Chart ticketSizeComparison(List<EvidenceSnapshot.NamedAreaCustomers> areas) {
        AreaCustomerResult first = areas.get(0).result();
        return new Chart(
                "ticketSize",
                "bar",
                "건당 결제액 비교",
                List.of("도민", "관광객"),
                areas.stream().map(a -> new ChartDataset(a.name(), Arrays.asList(
                        a.result().residentTicket() == null ? null : a.result().residentTicket().doubleValue(),
                        a.result().touristTicket() == null ? null : a.result().touristTicket().doubleValue()),
                        null)).toList(),
                "원",
                source(first.meta(), first.meta().period()),
                first.meta().scope()
        );
    }

    /** 동네마다 도민과 관광객 선을 다 그리면 선이 너무 많아, 동네별 외식 결제 합계(도민 + 관광객) 한 줄씩 그린다. */
    private Chart areaMonthlyComparison(List<EvidenceSnapshot.NamedAreaCustomers> areas) {
        AreaCustomerResult first = areas.get(0).result();
        List<String> months = first.residentMonthly().stream().map(MonthlyPoint::ym).toList();
        List<ChartDataset> datasets = new ArrayList<>();
        for (EvidenceSnapshot.NamedAreaCustomers area : areas) {
            List<MonthlyPoint> resident = area.result().residentMonthly();
            List<MonthlyPoint> tourist = area.result().touristMonthly();
            if (!resident.stream().map(MonthlyPoint::ym).toList().equals(months) || tourist.size() != resident.size()) {
                return null; // 기간이 다르면 나란히 그릴 수 없어 기본 차트로 돌아간다.
            }
            List<Double> totals = new ArrayList<>();
            for (int i = 0; i < resident.size(); i++) {
                Double r = resident.get(i).value();
                Double t = tourist.get(i).value();
                totals.add(r == null || t == null ? null : round(r + t));
            }
            datasets.add(new ChartDataset(area.name(), totals, null));
        }
        return new Chart(
                "areaMonthly",
                "line",
                "동네 외식 결제 월별 추이 비교",
                months,
                datasets,
                "원",
                source(first.meta(), first.meta().period()),
                first.meta().scope()
        );
    }

    private Chart customerMix(AreaCustomerResult result) {
        if (result == null) {
            return null;
        }
        return new Chart(
                "customerMix",
                "doughnut",
                "동네 손님 구성",
                List.of("도민", "내국인 관광객", "외국인 관광객"),
                List.of(new ChartDataset(
                        "결제 비중",
                        List.of(result.residentShare(), result.domesticShare(), result.foreignShare()),
                        null
                )),
                "%",
                source(result.meta(), result.meta().period()),
                result.meta().scope()
        );
    }

    private Chart areaMonthly(AreaCustomerResult result) {
        if (result == null) {
            return null;
        }
        return new Chart(
                "areaMonthly",
                "line",
                "동네 외식 결제 월별 추이",
                result.residentMonthly().stream().map(MonthlyPoint::ym).toList(),
                List.of(
                        new ChartDataset("도민", values(result.residentMonthly()), null),
                        new ChartDataset("관광객", values(result.touristMonthly()), null),
                        new ChartDataset("제주 동 평균", values(result.jejuAvgMonthly()), true)
                ),
                "원",
                source(result.meta(), result.meta().period()),
                result.meta().scope()
        );
    }

    private Chart ticketSize(AreaCustomerResult result) {
        if (result == null) {
            return null;
        }
        return new Chart(
                "ticketSize",
                "bar",
                "건당 결제액",
                List.of("도민", "관광객"),
                List.of(new ChartDataset(
                        "건당 결제액",
                        Arrays.asList(
                                result.residentTicket() == null ? null : result.residentTicket().doubleValue(),
                                result.touristTicket() == null ? null : result.touristTicket().doubleValue()
                        ),
                        null
                )),
                "원",
                source(result.meta(), result.meta().period()),
                result.meta().scope()
        );
    }

    private Chart touristFlow(AreaCustomerResult result) {
        if (result == null || result.touristFlow() == null) {
            return null;
        }
        TouristFlow flow = result.touristFlow();
        return new Chart(
                "touristFlow",
                "line",
                "최근 4주 입도객 흐름",
                flow.thisYear().stream().map(SeriesPoint::label).toList(),
                List.of(
                        new ChartDataset("올해", seriesValues(flow.thisYear()), null),
                        new ChartDataset("작년", seriesValues(flow.lastYear()), null)
                ),
                "명",
                source(result.meta(), flow.period()),
                result.meta().scope()
        );
    }

    private Chart industrySeason(IndustryTourismResult result) {
        if (result == null) {
            return null;
        }
        return new Chart(
                "industrySeason",
                "line",
                "업종 월별 계절 지수",
                result.seasonIndex().stream().map(MonthlyPoint::ym).toList(),
                List.of(new ChartDataset("계절 지수", values(result.seasonIndex()), null)),
                "지수",
                source(result.meta(), result.meta().period()),
                result.meta().scope()
        );
    }

    private Chart competition(CompetitionResult result) {
        if (result == null) {
            return null;
        }
        return new Chart(
                "competition",
                "bar",
                "동네 업종별 점포 수",
                List.of("동네", "제주 평균"),
                List.of(new ChartDataset(
                        "점포 수",
                        List.of((double) result.storeCount(), result.jejuAvg()),
                        null
                )),
                "개",
                source(result.meta(), result.meta().period()),
                // 차트 숫자는 반경을 물었어도 항상 동네 점포 수와 제주 평균이다. 반경 점포 수는 근거(meta)에만 남는다.
                "AREA"
        );
    }

    private Chart myStoreVsArea(AreaCustomerResult area, MyStoreInput myStore) {
        if (area == null || myStore == null || area.yoyChange() == null
                || myStore.monthlySales() == null || myStore.monthlySales().isEmpty()) {
            return null;
        }
        Double myStoreYoy = latestYearOverYear(myStore.monthlySales());
        if (myStoreYoy == null) {
            return myStoreMonthly(myStore.monthlySales());
        }
        return new Chart(
                "myStoreVsArea",
                "bar",
                "내 가게와 동네 전년 대비 변화",
                List.of("내 가게", "동네 외식"),
                List.of(new ChartDataset(
                        "전년 대비 변화",
                        List.of(myStoreYoy, round(area.yoyChange())),
                        null
                )),
                "%",
                source(area.meta(), area.meta().period()),
                area.meta().scope()
        );
    }

    /** 작년 같은 달 매출이 없어 전년 대비를 낼 수 없으면, 입력한 월매출을 달 순서대로 그대로 보여 준다. */
    private static Chart myStoreMonthly(List<MonthlySale> monthlySales) {
        List<MonthlySale> sorted = monthlySales.stream()
                .sorted(Comparator.comparing(MonthlySale::month))
                .toList();
        return new Chart(
                "myStoreVsArea",
                "line",
                "내 가게 월매출",
                sorted.stream().map(MonthlySale::month).toList(),
                List.of(new ChartDataset(
                        "내 가게 월매출",
                        sorted.stream().map(sale -> (double) sale.amount()).toList(),
                        null
                )),
                "원",
                "사용자 입력 월매출",
                "AREA"
        );
    }

    private static Double latestYearOverYear(List<MonthlySale> monthlySales) {
        Map<YearMonth, Long> amountsByMonth = new HashMap<>();
        for (MonthlySale sale : monthlySales) {
            amountsByMonth.put(YearMonth.parse(sale.month()), sale.amount());
        }
        return amountsByMonth.keySet().stream()
                .filter(month -> amountsByMonth.containsKey(month.minusYears(1)))
                .filter(month -> amountsByMonth.get(month.minusYears(1)) != 0)
                .max(YearMonth::compareTo)
                .map(month -> round(
                        (amountsByMonth.get(month) / (double) amountsByMonth.get(month.minusYears(1)) - 1) * 100
                ))
                .orElse(null);
    }

    private static List<Double> values(List<MonthlyPoint> points) {
        return points.stream().map(MonthlyPoint::value).toList();
    }

    private static List<Double> seriesValues(List<SeriesPoint> points) {
        return points.stream().map(SeriesPoint::value).toList();
    }

    private static String source(MetricMeta meta, String period) {
        return period == null ? meta.source() : meta.source() + " (" + period + ")";
    }

    private static double round(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
