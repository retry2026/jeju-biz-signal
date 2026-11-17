import type { MonthlySale } from './monthlySales.js'

export type UserType = 'STARTUP' | 'EXISTING'
export type ResponseType = 'ANSWER' | 'REFUSE' | 'ASK_BACK'
export type ChartType = 'doughnut' | 'bar' | 'line'

export interface Area {
  code: string
  name: string
  city: string
}

export interface Industry {
  code: string
  name: string
}

export interface MetaResponse {
  areas: Area[]
  industries: Industry[]
  exampleQuestions: Record<UserType, string[]>
  dataAsOf: {
    store: string
    areaSpend: string
    industrySpend: string
    arrivals: string
  }
}

export interface ChatRequest {
  sessionId: string
  userType: UserType
  area: string
  industry: string
  myStore: { monthlySales: MonthlySale[] } | null
  message: string
}

export interface ChatSection {
  id: string
  title: string
  text: string
}

export interface ChatChart {
  id: string
  type: ChartType
  title: string
  labels: string[]
  datasets: { label: string; data: (number | null)[]; dashed?: boolean | null }[]
  unit: string
  source: string
  scope: string
}

export interface Evidence {
  tool: string
  source: string
  period: string
  scope: string
  lowSample: boolean
}

export interface ChatResponse {
  type: ResponseType
  summary: string
  sections: ChatSection[]
  charts: ChatChart[]
  evidence: Evidence[]
  limits: string[]
  suggestions: string[]
  askFor?: 'AREA' | 'INDUSTRY' | 'MESSAGE' | 'MY_STORE' | null
}
