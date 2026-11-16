import type { ChatResponse } from '../api'
import { ChartView } from './ChartView'

const sectionOrder = ['competition', 'customers', 'industry', 'tourism', 'myStore', 'advice']

interface ResultViewProps {
  response: ChatResponse
  onSuggestion: (suggestion: string) => void
  disabled: boolean
}

export function ResultView({ response, onSuggestion, disabled }: ResultViewProps) {
  const sections = [...response.sections].sort((a, b) => {
    const aOrder = sectionOrder.indexOf(a.id)
    const bOrder = sectionOrder.indexOf(b.id)
    return (aOrder < 0 ? sectionOrder.length : aOrder) - (bOrder < 0 ? sectionOrder.length : bOrder)
  })

  return (
    <article className={`result-card result-${response.type.toLowerCase()}`}>
      <p className="result-type">
        {response.type === 'ANSWER' ? '분석 결과' : response.type === 'REFUSE' ? '분석 범위 안내' : '추가 정보가 필요해요'}
      </p>
      <h3>{response.summary}</h3>

      {sections.length > 0 && (
        <div className="result-sections">
          {sections.map((section) => (
            <section className="result-section" key={section.id}>
              <h4>{section.title}</h4>
              <p>{section.text}</p>
            </section>
          ))}
        </div>
      )}

      {response.charts.length > 0 && (
        <div className="chart-list">
          {response.charts.map((chart) => <ChartView chart={chart} key={chart.id} />)}
        </div>
      )}

      {response.evidence.length > 0 && (
        <details className="evidence">
          <summary>근거 데이터 보기</summary>
          <ul>
            {response.evidence.map((item, index) => (
              <li key={`${item.tool}-${index}`}>
                {item.source} · {item.period} · {item.scope}
                {item.lowSample && ' · 표본이 적어 기간을 넓혀 계산'}
              </li>
            ))}
          </ul>
        </details>
      )}

      {response.limits.length > 0 && (
        <section className="limits">
          <h4>해석할 때 참고해 주세요</h4>
          <ul>{response.limits.map((limit) => <li key={limit}>{limit}</li>)}</ul>
        </section>
      )}

      {response.suggestions.length > 0 && (
        <div className="result-suggestions" aria-label="다음 질문 제안">
          {response.suggestions.map((suggestion) => (
            <button
              className="suggestion-button"
              disabled={disabled}
              key={suggestion}
              onClick={() => onSuggestion(suggestion)}
              type="button"
            >
              {suggestion}
            </button>
          ))}
        </div>
      )}
    </article>
  )
}
