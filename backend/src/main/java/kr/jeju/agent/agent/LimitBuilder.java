package kr.jeju.agent.agent;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import kr.jeju.agent.metric.MetricMeta;

/** 실제로 쓴 도구 결과의 출처와 기준으로 한계 문구를 만든다 (FR-24). */
public class LimitBuilder {

    public List<String> build(List<MetricMeta> metas) {
        Set<String> limits = new LinkedHashSet<>();
        for (MetricMeta meta : metas) {
            String source = meta.source() == null ? "" : meta.source();
            if (source.contains("상가")) {
                limits.add("점포 수는 2025년 9월 한 시점 기준이며, 제주 동 평균은 해당 업종 가게가 없는 동도 포함한 값입니다.");
            }
            if (source.contains("지역별 소비")) {
                limits.add("동네 손님 구성은 음식점업 전체 기준입니다.");
                limits.add("카드 데이터는 BC카드 원천이며 지역별 소비는 점유율 보정 추정치입니다.");
            }
            if ("JEJU".equals(meta.scope())) {
                limits.add("업종 성격은 동네가 아닌 제주 전체 기준입니다.");
            }
            if (meta.lowSample()) {
                limits.add("표본이 적어 기간을 넓혀 계산했습니다.");
            }
        }
        limits.add("과거 데이터 기반이며 예측이 아닙니다.");
        return List.copyOf(limits);
    }
}
