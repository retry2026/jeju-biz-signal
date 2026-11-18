import type { ChatResponse } from '../api'
import { keyMetrics, scopeLabel } from '../resultMetrics.js'
import { ChartView } from './ChartView'

const sectionOrder = ['competition', 'customers', 'industry', 'tourism', 'myStore', 'advice']

interface ResultViewProps {
  context: string
  response: ChatResponse
  onSuggestion: (suggestion: string) => void
  disabled: boolean
}

export function ResultView({ context, response, onSuggestion, disabled }: ResultViewProps) {
  const sections = [...response.sections].sort((a, b) => {
    const aOrder = sectionOrder.indexOf(a.id)
    const bOrder = sectionOrder.indexOf(b.id)
    return (aOrder < 0 ? sectionOrder.length : aOrder) - (bOrder < 0 ? sectionOrder.length : bOrder)
  })

  const suggestions = response.suggestions.length > 0 && (
    <div className="next-questions" aria-label="다음 질문 제안">
      {response.suggestions.map((suggestion) => (
        <button
          className="next-button"
          disabled={disabled}
          key={suggestion}
          onClick={() => onSuggestion(suggestion)}
          type="button"
        >
          {suggestion} <span aria-hidden="true">→</span>
        </button>
      ))}
    </div>
  )

  if (response.type !== 'ANSWER') {
    return (
      <article className={`answer answer-notice answer-${response.type.toLowerCase()}${response.suggestions.length === 0 ? ' is-single' : ''}`}>
        <div>
          <p className="answer-kicker">{response.type === 'REFUSE' ? '분석 범위 안내' : '조금 더 알려 주세요'}</p>
          <h3 className="answer-summary">{response.summary}</h3>
          {sections.map((section) => <p className="notice-text" key={section.id}>{section.text}</p>)}
        </div>
        {response.suggestions.length > 0 && (
          <div>
            <p className="notice-side-title">{response.type === 'REFUSE' ? '대신 이렇게 물어보세요' : '이렇게 물어보면 답할 수 있어요'}</p>
            {suggestions}
          </div>
        )}
      </article>
    )
  }

  const metrics = keyMetrics(response.charts)
  const advice = sections.filter((section) => section.id === 'advice')
  const body = sections.filter((section) => section.id !== 'advice')

  return (
    <article className="answer">
      <p className="answer-kicker">분석 결과 · {context}</p>
      <h3 className="answer-summary">{response.summary}</h3>

      {metrics.length > 0 && (
        <div className="metric-row">
          {metrics.map((metric, index) => (
            <div className={`tile tile-metric${index === 0 ? ' is-lead' : ''}`} key={metric.label}>
              <p className="metric-label">{metric.label}</p>
              <p className="metric-value">{metric.value}<small>{metric.unit}</small></p>
              <p className="metric-note">{metric.note}</p>
            </div>
          ))}
        </div>
      )}

      <div className="bento">
        {response.charts.map((chart) => (
          <div className="tile tile-chart" key={chart.id}>
            <ChartView chart={chart} />
          </div>
        ))}
        {body.map((section) => (
          <section className="tile tile-text" key={section.id}>
            <h4>{section.title}</h4>
            <p>{section.text}</p>
          </section>
        ))}
        {advice.map((section) => (
          <section className="tile tile-text tile-advice" key={section.id}>
            <h4>{section.title}</h4>
            <p>{section.text}</p>
          </section>
        ))}
      </div>

      {(response.evidence.length > 0 || response.limits.length > 0) && (
        <details className="answer-details">
          <summary>근거 데이터와 해석할 때 참고할 점</summary>
          {response.evidence.length > 0 && (
            <ul>
              {response.evidence.map((item, index) => (
                <li key={`${item.tool}-${index}`}>
                  {item.source} · {item.period} · {scopeLabel(item.scope)}
                  {item.lowSample && ' · 표본이 적어 기간을 넓혀 계산'}
                </li>
              ))}
            </ul>
          )}
          {response.limits.length > 0 && (
            <ul className="limits">{response.limits.map((limit) => <li key={limit}>{limit}</li>)}</ul>
          )}
        </details>
      )}

      {suggestions}
    </article>
  )
}
