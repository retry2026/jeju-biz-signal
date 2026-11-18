import { useState, type FormEvent, type KeyboardEvent } from 'react'
import type { UserType } from '../api'
import { parseMonthlySales, type MonthlySale, type MonthlySaleDraft } from '../monthlySales.js'
import { ResultView } from './ResultView'

export interface ChatTurn {
  question: string
  response: import('../api').ChatResponse
}

interface ChatPageProps {
  userType: UserType
  area: string
  industry: string
  exampleQuestions: string[]
  monthlySalesDrafts: MonthlySaleDraft[]
  onMonthlySalesDraftsChange: (drafts: MonthlySaleDraft[]) => void
  turns: ChatTurn[]
  pendingQuestion: string
  isSending: boolean
  error: string
  onSend: (message: string, monthlySales: MonthlySale[]) => Promise<boolean>
  onBack: () => void
}

/**
 * 브라우저마다 달 입력 모양이 달라 직접 타이핑하지 않도록 목록에서 고르게 한다.
 * 매출이 확정된 지난달부터 25개월(작년 같은 달 포함)을 최신순으로 둔다.
 */
const monthOptions = Array.from({ length: 25 }, (_, offset) => {
  const date = new Date()
  date.setDate(1)
  date.setMonth(date.getMonth() - 1 - offset)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
})
const presetLengths = [3, 6, 12]
const formatAmount = (digits: string) => (digits === '' ? '' : Number(digits).toLocaleString('ko-KR'))

const salesErrorMessages = {
  TOO_MANY: '월매출은 최대 12개월까지 입력할 수 있어요.',
  INCOMPLETE: '월과 금액을 모두 입력하거나 입력 행을 삭제해 주세요.',
  INVALID_MONTH: '월을 올바르게 선택해 주세요.',
  DUPLICATE_MONTH: '같은 달을 두 번 입력할 수 없어요.',
  INVALID_AMOUNT: '매출은 만원 단위 숫자로 입력해 주세요.',
}

export function ChatPage({
  userType,
  area,
  industry,
  exampleQuestions,
  monthlySalesDrafts,
  onMonthlySalesDraftsChange,
  turns,
  pendingQuestion,
  isSending,
  error,
  onSend,
  onBack,
}: ChatPageProps) {
  const [message, setMessage] = useState('')
  const [salesError, setSalesError] = useState('')
  const [salesEditorOpen, setSalesEditorOpen] = useState(false)
  const needsSales = turns.at(-1)?.response.askFor === 'MY_STORE'
  const showSalesEditor = salesEditorOpen || needsSales

  const send = async (value: string) => {
    const parsedSales = parseMonthlySales(monthlySalesDrafts)
    if (parsedSales.error) {
      setSalesError(salesErrorMessages[parsedSales.error])
      setSalesEditorOpen(true)
      return
    }
    setSalesError('')
    const sent = await onSend(value, parsedSales.monthlySales)
    if (sent) setMessage('')
  }

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    void send(message)
  }

  const handleKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      event.currentTarget.form?.requestSubmit()
    }
  }

  const sendSuggestion = (suggestion: string) => {
    setMessage(suggestion)
    void send(suggestion)
  }

  const updateSale = (index: number, field: keyof MonthlySaleDraft, value: string) => {
    onMonthlySalesDraftsChange(monthlySalesDrafts.map((sale, saleIndex) => (
      saleIndex === index ? { ...sale, [field]: value } : sale
    )))
    setSalesError('')
  }

  const context = `${area} · ${industry}`

  return (
    <section className="chat" aria-labelledby="chat-title">
      <aside className="chat-rail">
        <div className="rail-block">
          <div className="rail-heading">
            <h2 id="chat-title" className="rail-title">선택한 조건</h2>
            <button className="ghost-button small" onClick={onBack} type="button">바꾸기</button>
          </div>
          <dl className="condition-list">
            <dt>동네</dt><dd>{area}</dd>
            <dt>업종</dt><dd>{industry}</dd>
            <dt>상황</dt><dd>{userType === 'STARTUP' ? '창업 준비' : '가게 운영'}</dd>
          </dl>
        </div>

        {userType === 'EXISTING' && (
          <section className={`rail-block sales-panel${needsSales ? ' is-required' : ''}`} aria-labelledby="sales-title">
            <div className="rail-heading">
              <h3 id="sales-title" className="rail-title">내 월매출 <span>(선택)</span></h3>
              {!needsSales && (
                <button
                  className="ghost-button small"
                  type="button"
                  aria-expanded={showSalesEditor}
                  aria-controls="sales-editor"
                  onClick={() => setSalesEditorOpen((open) => !open)}
                >
                  {showSalesEditor ? '접기' : monthlySalesDrafts.length > 0 ? '수정' : '입력'}
                </button>
              )}
            </div>
            <p className="rail-note">
              {needsSales
                ? '내 가게와 비교하려면 월매출을 입력한 뒤 질문을 다시 보내 주세요.'
                : '최근 1~12개월 매출을 넣으면 동네 흐름과 나란히 봅니다. 작년 같은 달도 넣으면 전년 대비를 비교합니다. 금액은 만원 단위입니다.'}
            </p>
            {showSalesEditor && (
              <div id="sales-editor" className="sales-editor">
                <div className="sales-presets" role="group" aria-label="최근 몇 개월을 입력할지 고르기">
                  {presetLengths.map((length) => (
                    <button
                      className="ghost-button small"
                      key={length}
                      type="button"
                      onClick={() => {
                        // 이미 적은 금액은 같은 달이면 그대로 두고, 지난달부터 최신순으로 칸을 채운다.
                        const amounts = new Map(monthlySalesDrafts.map((sale) => [sale.month, sale.amount]))
                        onMonthlySalesDraftsChange(monthOptions.slice(0, length).map((month) => ({ month, amount: amounts.get(month) ?? '' })))
                        setSalesError('')
                      }}
                    >
                      최근 {length}개월
                    </button>
                  ))}
                </div>
                {monthlySalesDrafts.map((sale, index) => (
                  <div className="sales-row" key={`sale-${index}`}>
                    <label>
                      <span>월 {index + 1}</span>
                      <select
                        value={sale.month}
                        onChange={(event) => updateSale(index, 'month', event.target.value)}
                      >
                        <option value="">달 선택</option>
                        {monthOptions.map((month) => (
                          <option key={month} value={month}>{month.slice(0, 4)}년 {Number(month.slice(5))}월</option>
                        ))}
                      </select>
                    </label>
                    <label>
                      <span>매출액 (만원)</span>
                      <input
                        type="text"
                        inputMode="numeric"
                        maxLength={11}
                        placeholder="예: 1,250"
                        value={formatAmount(sale.amount)}
                        onChange={(event) => updateSale(index, 'amount', event.target.value.replace(/\D/g, ''))}
                      />
                    </label>
                    <button
                      className="ghost-button small sales-remove"
                      type="button"
                      aria-label={`매출 월 ${index + 1} 삭제`}
                      onClick={() => {
                        onMonthlySalesDraftsChange(monthlySalesDrafts.filter((_, saleIndex) => saleIndex !== index))
                        setSalesError('')
                      }}
                    >
                      삭제
                    </button>
                  </div>
                ))}
                <button
                  className="ghost-button small"
                  type="button"
                  disabled={monthlySalesDrafts.length >= 12}
                  onClick={() => {
                    onMonthlySalesDraftsChange([...monthlySalesDrafts, { month: '', amount: '' }])
                    setSalesError('')
                  }}
                >
                  + 월 추가 ({monthlySalesDrafts.length}/12)
                </button>
                {salesError && <p className="form-error" role="alert">{salesError}</p>}
              </div>
            )}
          </section>
        )}

        <div className="rail-block">
          <h3 className="rail-title">이렇게 물어볼 수 있어요</h3>
          <div className="example-list">
            {exampleQuestions.map((question) => (
              <button
                className="example-button"
                disabled={isSending}
                key={question}
                onClick={() => sendSuggestion(question)}
                type="button"
              >
                {question}
              </button>
            ))}
          </div>
        </div>
      </aside>

      <div className="chat-main">
        <div className="thread" aria-live="polite">
          {turns.length === 0 && !pendingQuestion && (
            <div className="thread-empty">
              <p className="thread-empty-title">{context}에 대해 무엇이든 물어보세요</p>
              <p>왼쪽 예시 질문을 누르거나 아래 입력칸에 직접 적어 주세요.</p>
            </div>
          )}
          {turns.map((turn, index) => (
            <article className="turn" key={`${index}-${turn.question}`}>
              <p className="question-bubble">{turn.question}</p>
              <ResultView context={context} response={turn.response} onSuggestion={sendSuggestion} disabled={isSending} />
            </article>
          ))}
          {pendingQuestion && (
            <div className="turn">
              <p className="question-bubble">{pendingQuestion}</p>
              <p className="loading-message" role="status">데이터를 살펴보고 있어요…</p>
            </div>
          )}
          {error && <div className="notice-error" role="alert">{error}</div>}
        </div>

        <form className="composer" onSubmit={submit}>
          <label className="sr-only" htmlFor="question">질문 (최대 500자)</label>
          <textarea
            id="question"
            maxLength={500}
            onChange={(event) => setMessage(event.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="예: 겨울만 되면 매출이 줄어요. 정상인가요?"
            value={message}
            disabled={isSending}
            rows={2}
          />
          <div className="composer-side">
            <button className="send-button" disabled={isSending || !message.trim()} type="submit">
              {isSending ? '분석 중…' : '질문하기'}
            </button>
            <span className="composer-count">{message.length} / 500</span>
          </div>
        </form>
      </div>
    </section>
  )
}
