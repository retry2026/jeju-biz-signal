package kr.jeju.agent.metric;

import java.util.List;

public record AreaCustomerResult(
        String area,
        String period,
        double residentShare,
        double domesticShare,
        double foreignShare,
        double touristShare,
        double jejuAvgTouristShare,
        Long residentTicket,
        Long touristTicket,
        double seasonalRange,
        Double yoyChange,
        Double txnPerStore,
        List<MonthlyPoint> residentMonthly,
        List<MonthlyPoint> touristMonthly,
        List<MonthlyPoint> jejuAvgMonthly,
        TouristFlow touristFlow,
        MetricMeta meta
) {}
