import { useCallback, useEffect, useRef, useState } from 'react'
import { getMeta, sendChat, type ChatResponse, type MetaResponse, type UserType } from './api'
import { ChatPage, type ChatTurn } from './components/ChatPage'
import { ConditionPage } from './components/ConditionPage'
import { StartPage } from './components/StartPage'
import { useIdleReset } from './hooks/useIdleReset'
import type { MonthlySale, MonthlySaleDraft } from './monthlySales.js'
import './App.css'

type Page = 'start' | 'conditions' | 'chat'

function createSessionId() {
  return crypto.randomUUID()
}

function App() {
  const [meta, setMeta] = useState<MetaResponse | null>(null)
  const [metaError, setMetaError] = useState('')
  const [page, setPage] = useState<Page>('start')
  const [userType, setUserType] = useState<UserType | null>(null)
  const [area, setArea] = useState('')
  const [industry, setIndustry] = useState('')
  const [monthlySalesDrafts, setMonthlySalesDrafts] = useState<MonthlySaleDraft[]>([])
  const [sessionId, setSessionId] = useState(createSessionId)
  const [turns, setTurns] = useState<ChatTurn[]>([])
  const [pendingQuestion, setPendingQuestion] = useState('')
  const [isSending, setIsSending] = useState(false)
  const [chatError, setChatError] = useState('')
  const sendingRef = useRef(false)
  const activeRequestRef = useRef<AbortController | null>(null)
  const requestIdRef = useRef(0)

  const loadMeta = useCallback(async () => {
    try {
      const response = await getMeta()
      setMeta(response)
      setMetaError('')
    } catch (error) {
      setMetaError(error instanceof Error ? error.message : '화면 정보를 불러오지 못했습니다.')
    }
  }, [])

  useEffect(() => {
    let active = true
    getMeta()
      .then((response) => {
        if (active) {
          setMeta(response)
          setMetaError('')
        }
      })
      .catch((error: unknown) => {
        if (active) {
          setMetaError(error instanceof Error ? error.message : '화면 정보를 불러오지 못했습니다.')
        }
      })
    return () => {
      active = false
    }
  }, [])

  const reset = useCallback(() => {
    requestIdRef.current += 1
    activeRequestRef.current?.abort()
    activeRequestRef.current = null
    setPage('start')
    setUserType(null)
    setArea('')
    setIndustry('')
    setMonthlySalesDrafts([])
    setTurns([])
    setPendingQuestion('')
    setIsSending(false)
    sendingRef.current = false
    setChatError('')
    setSessionId(createSessionId())
  }, [])

  useIdleReset(reset)

  const startConversation = (selectedUserType: UserType) => {
    setUserType(selectedUserType)
    setPage('conditions')
  }

  const openChat = () => {
    if (!userType || !area || !industry) return
    setPage('chat')
  }

  const sendMessage = async (message: string, monthlySales: MonthlySale[]): Promise<boolean> => {
    const trimmed = message.trim()
    if (!trimmed || !userType || !area || !industry || sendingRef.current) return false

    sendingRef.current = true
    const requestId = ++requestIdRef.current
    const controller = new AbortController()
    activeRequestRef.current = controller
    setIsSending(true)
    setChatError('')
    setPendingQuestion(trimmed)

    try {
      const response: ChatResponse = await sendChat({
        sessionId,
        userType,
        area,
        industry,
        myStore: monthlySales.length > 0 ? { monthlySales } : null,
        message: trimmed,
      }, controller.signal)
      if (requestId !== requestIdRef.current) return false
      setTurns((current) => [...current, { question: trimmed, response }])
      return true
    } catch (error) {
      if (requestId !== requestIdRef.current) return false
      setChatError(error instanceof Error ? error.message : '답변을 받지 못했습니다. 다시 시도해 주세요.')
      return false
    } finally {
      if (requestId === requestIdRef.current) {
        activeRequestRef.current = null
        sendingRef.current = false
        setIsSending(false)
        setPendingQuestion('')
      }
    }
  }

  const returnToConditions = () => {
    requestIdRef.current += 1
    activeRequestRef.current?.abort()
    activeRequestRef.current = null
    sendingRef.current = false
    setPage('conditions')
    setSessionId(createSessionId())
    setTurns([])
    setIsSending(false)
    setPendingQuestion('')
    setChatError('')
  }

  return (
    <main className="app-shell">
      <header className="app-header">
        <div>
          <p className="eyebrow">제주 상권 신호등</p>
          <h1>도민일까, 관광객일까</h1>
        </div>
        {page !== 'start' && (
          <button className="text-button" type="button" onClick={reset}>
            처음으로
          </button>
        )}
      </header>

      {metaError && (
        <div className="notice notice-error" role="alert">
          <span>{metaError}</span>
          <button className="text-button" type="button" onClick={() => void loadMeta()}>
            다시 불러오기
          </button>
        </div>
      )}

      {!meta && !metaError && <p className="loading-message" role="status">화면 정보를 불러오는 중입니다…</p>}

      {meta && page === 'start' && (
        <StartPage onSelect={startConversation} />
      )}

      {meta && page === 'conditions' && userType && (
        <ConditionPage
          areas={meta.areas}
          industries={meta.industries}
          selectedArea={area}
          selectedIndustry={industry}
          onAreaChange={setArea}
          onIndustryChange={setIndustry}
          onContinue={openChat}
        />
      )}

      {meta && page === 'chat' && userType && (
        <ChatPage
          userType={userType}
          area={meta.areas.find((item) => item.code === area)?.name ?? area}
          industry={meta.industries.find((item) => item.code === industry)?.name ?? industry}
          exampleQuestions={meta.exampleQuestions[userType]}
          monthlySalesDrafts={monthlySalesDrafts}
          onMonthlySalesDraftsChange={setMonthlySalesDrafts}
          turns={turns}
          pendingQuestion={pendingQuestion}
          isSending={isSending}
          error={chatError}
          onSend={sendMessage}
          onBack={returnToConditions}
        />
      )}

      {meta && (
        <footer className="app-footer">
          데이터 기준일 · 점포 {meta.dataAsOf.store} · 동네 소비 {meta.dataAsOf.areaSpend} · 업종 카드 {meta.dataAsOf.industrySpend} · 입도객 {meta.dataAsOf.arrivals}
        </footer>
      )}
    </main>
  )
}

export default App
