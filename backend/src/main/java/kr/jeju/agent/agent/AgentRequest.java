package kr.jeju.agent.agent;

import java.util.List;

/**
 * 에이전트 입력. web의 요청 DTO와 독립된 ③의 타입이다. web이 자기 DTO를 이 타입으로 바꿔 넘긴다.
 *
 * @param userType     STARTUP 또는 EXISTING
 * @param area         행정동 코드 8자리
 * @param industry     업종 코드 (CAFE, KOREAN, ...)
 * @param monthlySales 기존 사업자가 입력한 월매출. 없으면 빈 목록
 */
public record AgentRequest(
        String sessionId,
        String userType,
        String area,
        String industry,
        List<MonthlySale> monthlySales,
        String message) {

    public AgentRequest {
        monthlySales = monthlySales == null ? List.of() : List.copyOf(monthlySales);
    }

    /** @param month "YYYY-MM", @param amount 원 */
    public record MonthlySale(String month, Long amount) {
    }
}
