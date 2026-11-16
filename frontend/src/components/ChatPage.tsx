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

  const salesErrorMessages = {
    TOO_MANY: '월매출은 최대 12개월까지 입력할 수 있어요.',
    INCOMPLETE: '월과 금액을 모두 입력하거나 입력 행을 삭제해 주세요.',
    INVALID_MONTH: '월을 올바르게 선택해 주세요.',
    DUPLICATE_MONTH: '같은 달을 두 번 입력할 수 없어요.',
    INVALID_AMOUNT: '매출은 0 이상의 정수(원)로 입력해 주세요.',
  }

  return (
    <section className="chat-page" aria-labelledby="chat-title">
      <div className="chat-heading">
        <div>
          <p className="step-label">3 / 3 · 질문과 분석</p>
          <h2 id="chat-title">무엇이 궁금하세요?</h2>
        </div>
        <button className="text-button" onClick={onBack} type="button">조건 바꾸기</button>
      </div>
      <div className="condition-summary">
        <span>{area}</span><span>{industry}</span>
        <span>{userType === 'STARTUP' ? '창업 준비' : '기존 사업자'}</span>
      </div>

      {userType === 'EXISTING' && (
        <section className="sales-panel" aria-labelledby="sales-title">
          <div className="sales-heading">
            <div>
              <h3 id="sales-title">내 월매출 정보 <span>(선택)</span></h3>
              <p>비교가 필요하면 최근 1~12개월 매출을 입력해 주세요. 금액은 원 단위입니다.</p>
            </div>
            {!needsSales && (
              <button
                className="text-button"
                type="button"
                aria-expanded={showSalesEditor}
                aria-controls="sales-editor"
                onClick={() => setSalesEditorOpen((open) => !open)}
              >
                {showSalesEditor ? '접기' : monthlySalesDrafts.length > 0 ? '수정' : '입력하기'}
              </button>
            )}
          </div>
          {needsSales && (
            <p className="sales-required" role="status">내 가게 비교를 위해 월매출을 입력한 뒤 질문을 다시 보내 주세요.</p>
          )}
          {showSalesEditor && (
            <div id="sales-editor" className="sales-editor">
              {monthlySalesDrafts.map((sale, index) => (
                <div className="sales-row" key={`sale-${index}`}>
                  <label>
                    <span>매출 월 {index + 1}</span>
                    <input
                      type="month"
                      value={sale.month}
                      onChange={(event) => updateSale(index, 'month', event.target.value)}
                    />
                  </label>
                  <label>
                    <span>매출액 (원)</span>
                    <input
                      type="number"
                      min="0"
                      step="1"
                      inputMode="numeric"
                      value={sale.amount}
                      onChange={(event) => updateSale(index, 'amount', event.target.value)}
                    />
                  </label>
                  <button
                    className="text-button sales-remove"
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
                className="text-button"
                type="button"
                disabled={monthlySalesDrafts.length >= 12}
                onClick={() => {
                  onMonthlySalesDraftsChange([...monthlySalesDrafts, { month: '', amount: '' }])
                  setSalesError('')
                }}
              >
                + 월 추가 ({monthlySalesDrafts.length}/12)
              </button>
              {salesError && <p className="sales-error" role="alert">{salesError}</p>}
            </div>
          )}
        </section>
      )}

      {turns.length === 0 && (
        <div className="examples">
          <p>예시 질문을 눌러 바로 물어볼 수 있어요.</p>
          <div className="suggestion-list">
            {exampleQuestions.map((question) => (
              <button
                className="suggestion-button"
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
      )}

      <div className="conversation" aria-live="polite">
        {turns.map((turn, index) => (
          <article className="conversation-turn" key={`${index}-${turn.question}`}>
            <div className="user-bubble">{turn.question}</div>
            <ResultView response={turn.response} onSuggestion={sendSuggestion} disabled={isSending} />
          </article>
        ))}
        {pendingQuestion && (
          <div className="pending-turn">
            <div className="user-bubble">{pendingQuestion}</div>
            <p className="loading-message" role="status">데이터를 살펴보고 있어요…</p>
          </div>
        )}
      </div>

      {error && <div className="notice notice-error" role="alert">{error}</div>}

      <form className="chat-form" onSubmit={submit}>
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
        <div className="chat-form-footer">
          <span>{message.length} / 500 · Enter로 전송, Shift+Enter로 줄바꿈</span>
          <button className="primary-button" disabled={isSending || !message.trim()} type="submit">
            {isSending ? '분석 중…' : '질문 보내기'}
          </button>
        </div>
      </form>
    </section>
  )
}
