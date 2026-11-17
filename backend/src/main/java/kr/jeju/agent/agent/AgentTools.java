package kr.jeju.agent.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import kr.jeju.agent.metric.AreaCustomerMetric;
import kr.jeju.agent.metric.CompetitionMetric;
import kr.jeju.agent.metric.IndustryTourismMetric;

/**
 * LLM이 부르는 도구 3개. 요청마다 새로 만들고, 호출 결과를 EvidenceCollector에 남긴다.
 * 지표 계산이 실패하면(없는 코드, 데이터 부족) 예외 대신 LLM이 읽고 거절하거나 대안을 제시할 수 있는 문장을 돌려준다.
 */
public class AgentTools {

    private static final Logger log = LoggerFactory.getLogger(AgentTools.class);

    private final CompetitionMetric competitionMetric;
    private final AreaCustomerMetric areaCustomerMetric;
    private final IndustryTourismMetric industryTourismMetric;
    private final EvidenceCollector collector;
    private final int maxCalls;

    public AgentTools(CompetitionMetric competitionMetric, AreaCustomerMetric areaCustomerMetric,
            IndustryTourismMetric industryTourismMetric, EvidenceCollector collector, int maxCalls) {
        this.competitionMetric = competitionMetric;
        this.areaCustomerMetric = areaCustomerMetric;
        this.industryTourismMetric = industryTourismMetric;
        this.collector = collector;
        this.maxCalls = maxCalls;
    }

    @Tool(name = "competition", description = """
            동네(행정동)와 반경 안에 같은 업종 가게가 몇 곳인지, 제주 동 평균의 몇 배인지 돌려준다.
            창업 입지, 경쟁, 과밀 질문에 쓴다. 기준: 상가정보 2025-09 한 시점.
            제주 동 평균은 해당 업종 가게가 없는 동도 0개로 넣어 계산한 값이다.""")
    public Object competition(
            @ToolParam(description = "행정동 코드 8자리") String area,
            @ToolParam(description = "업종 코드: CAFE, KOREAN, CHINESE, JAPANESE, WESTERN, PUB, FASTFOOD") String industry,
            @ToolParam(description = "반경(m). 반경 점포 수가 필요할 때만, 보통 500", required = false) Integer radiusM) {
        return call("competition", () -> {
            var result = competitionMetric.competition(area, industry, radiusM);
            collector.add(result);
            return result;
        });
    }

    @Tool(name = "areaCustomers", description = """
            동네 외식(음식점업 전체) 손님이 도민, 내국인 관광객, 외국인 관광객 중 누구인지 비중(%)과 관광객 합계 비중(touristShare, %),
            손님별 건당 결제액(원), 월별 결제 추이, 계절 변동폭(최대월/최소월), 전년 동월 대비(%),
            최근 4주 입도 관광객 전년 대비(%)를 돌려준다. 손님 구성, 관광 의존도, 계절, 매출 하락 원인 질문에 쓴다.
            기준: 동네 음식점업 전체(세부 업종 아님). 표본이 부족하면 자동으로 24개월로 넓혀 계산하고 meta.lowSample이 true가 된다.""")
    public Object areaCustomers(
            @ToolParam(description = "행정동 코드 8자리") String area,
            @ToolParam(description = "최근 몇 개월. 기본 12", required = false) Integer months) {
        return call("areaCustomers", () -> {
            var result = areaCustomerMetric.areaCustomers(area, months == null ? 12 : months);
            collector.add(result);
            return result;
        });
    }

    @Tool(name = "industryTourism", description = """
            업종의 결제액 중 제주도민, 도외 내국인, 외국인 비중(%)과 관광객 합계 비중(touristShare, %), 외국인 국적 구성(%),
            월별 계절 지수(연평균=100), 건당 결제액 전년 대비(%)를 돌려준다.
            업종이 관광객과 계절을 얼마나 타는지 질문에 쓴다. 기준: 제주 전체(동네 아님).""")
    public Object industryTourism(
            @ToolParam(description = "업종 코드: CAFE, KOREAN, CHINESE, JAPANESE, WESTERN, PUB, FASTFOOD") String industry,
            @ToolParam(description = "최근 몇 개월. 기본 12", required = false) Integer months) {
        return call("industryTourism", () -> {
            var result = industryTourismMetric.industryTourism(industry, months == null ? 12 : months);
            collector.add(result);
            return result;
        });
    }

    private Object call(String tool, java.util.function.Supplier<Object> body) {
        if (collector.callCount() >= maxCalls) {
            return "도구 호출 한도(" + maxCalls + "회)를 넘었습니다. 지금까지 받은 결과만으로 답하세요.";
        }
        try {
            return body.get();
        } catch (IllegalArgumentException | IllegalStateException | org.springframework.dao.DataAccessException e) {
            log.warn("도구 {} 실패: {}", tool, e.getMessage());
            return "이 조건으로는 데이터가 없어 계산할 수 없습니다 (" + e.getMessage() + "). "
                    + "이 항목은 숫자 없이 데이터가 없다고 밝히고, 다른 도구 결과나 대안 질문을 제안하세요.";
        }
    }
}
