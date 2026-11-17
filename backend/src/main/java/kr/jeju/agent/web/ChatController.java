package kr.jeju.agent.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import kr.jeju.agent.agent.AgentAnswer;
import kr.jeju.agent.agent.AgentRequest;
import kr.jeju.agent.agent.ChatAgent;
import kr.jeju.agent.chart.Chart;
import kr.jeju.agent.web.dto.ApiErrorResponse;
import kr.jeju.agent.web.dto.ChartDatasetDto;
import kr.jeju.agent.web.dto.ChartDto;
import kr.jeju.agent.web.dto.ChatRequest;
import kr.jeju.agent.web.dto.ChatResponse;
import kr.jeju.agent.web.dto.EvidenceDto;
import kr.jeju.agent.web.dto.SectionDto;

/**
 * POST /api/chat. 화면 DTO를 에이전트 입력으로 바꿔 부르고, 답을 화면 DTO로 돌려준다.
 * LLM 오류(LlmUnavailableException)는 ApiExceptionHandler가 503으로 바꾼다.
 */
@RestController
public class ChatController {

    private final ChatAgent chatAgent;
    private final SessionStore sessionStore;
    private final MetaController meta;

    public ChatController(ChatAgent chatAgent, SessionStore sessionStore, MetaController meta) {
        this.chatAgent = chatAgent;
        this.sessionStore = sessionStore;
        this.meta = meta;
        // 30분 동안 말이 없던 세션은 LLM 대화 기억도 지운다.
        sessionStore.onExpire(chatAgent::endSession);
    }

    @PostMapping("/api/chat")
    public ResponseEntity<?> chat(@RequestBody @Valid ChatRequest request) {
        if (!MetaController.INDUSTRY_CODES.contains(request.industry())) {
            return invalid("industry");
        }
        Set<String> areas = meta.areaCodes();
        if (!areas.isEmpty() && !areas.contains(request.area())) {
            return invalid("area");
        }

        sessionStore.touch(request.sessionId());
        AgentAnswer answer = chatAgent.answer(toAgentRequest(request));
        return ResponseEntity.ok(toResponse(answer));
    }

    private static ResponseEntity<ApiErrorResponse> invalid(String field) {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse("INVALID_REQUEST", field + " 값이 올바르지 않습니다.", field));
    }

    static AgentRequest toAgentRequest(ChatRequest r) {
        List<AgentRequest.MonthlySale> sales = r.myStore() == null || r.myStore().monthlySales() == null
                ? List.of()
                : r.myStore().monthlySales().stream()
                        .map(s -> new AgentRequest.MonthlySale(s.month(), s.amount()))
                        .toList();
        return new AgentRequest(r.sessionId(), r.userType(), r.area(), r.industry(), sales, r.message());
    }

    static ChatResponse toResponse(AgentAnswer a) {
        return new ChatResponse(a.type(), a.summary(),
                a.sections().stream().map(s -> new SectionDto(s.id(), s.title(), s.text())).toList(),
                a.charts().stream().map(ChatController::toChart).toList(),
                a.evidence().stream()
                        .map(e -> new EvidenceDto(e.tool(), e.source(), e.period(), e.scope(), e.lowSample()))
                        .toList(),
                a.limits(), a.suggestions(), a.askFor());
    }

    private static ChartDto toChart(Chart c) {
        List<ChartDatasetDto> datasets = c.datasets().stream()
                .map(d -> new ChartDatasetDto(d.label(), new ArrayList<Number>(d.data()), d.dashed()))
                .toList();
        return new ChartDto(c.id(), c.type(), c.title(), c.labels(), datasets, c.unit(), c.source(), c.scope());
    }
}
