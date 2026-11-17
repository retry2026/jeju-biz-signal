package kr.jeju.agent.chart;

import java.util.List;

public record ChartDataset(String label, List<Double> data, Boolean dashed) {}
