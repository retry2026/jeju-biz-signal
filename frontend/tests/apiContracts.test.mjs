import assert from 'node:assert/strict'
import test from 'node:test'
import { isChatResponse, isMetaResponse } from '../src/apiContracts.js'

const metaResponse = {
  areas: Array.from({ length: 43 }, (_, index) => ({
    code: String(50110000 + index).padStart(8, '0'),
    name: `동네${index + 1}`,
    city: index < 22 ? '제주시' : '서귀포시',
  })),
  industries: [
    { code: 'CAFE', name: '카페' },
    { code: 'KOREAN', name: '한식' },
    { code: 'CHINESE', name: '중식' },
    { code: 'JAPANESE', name: '일식' },
    { code: 'WESTERN', name: '양식' },
    { code: 'PUB', name: '주점' },
    { code: 'FASTFOOD', name: '패스트푸드' },
  ],
  exampleQuestions: {
    STARTUP: ['이 동네에 카페 차려도 괜찮을까요?'],
    EXISTING: ['겨울에 매출이 줄어요.'],
  },
  dataAsOf: {
    store: '2025-09-18',
    areaSpend: '2026-09',
    industrySpend: '2025-06',
    arrivals: '2026-11-12',
  },
}

const chatResponse = {
  type: 'ANSWER',
  summary: '동네 손님 구성입니다.',
  sections: [{ id: 'customers', title: '동네 손님', text: '상권 전체 기준입니다.' }],
  charts: [{
    id: 'customerMix',
    type: 'doughnut',
    title: '손님 구성',
    labels: ['도민'],
    datasets: [{ label: '결제액 비중', data: [100], dashed: null }],
    unit: '%',
    source: '지역별 소비',
    scope: 'AREA',
  }],
  evidence: [{ tool: 'areaCustomers', source: '지역별 소비', period: '2025-10 ~ 2026-09', scope: 'AREA', lowSample: false }],
  limits: ['과거 데이터 기준이며 예측이 아닙니다.'],
  suggestions: ['다른 동네와 비교해 볼까요?'],
  askFor: null,
}

test('accepts metadata matching the API contract and rejects missing required fields', () => {
  assert.equal(isMetaResponse(metaResponse), true)
  assert.equal(isMetaResponse({ ...metaResponse, dataAsOf: null }), false)
  assert.equal(isMetaResponse({ ...metaResponse, areas: [{ code: '50110660', name: '노형동', city: '제주시' }] }), false)
  assert.equal(isMetaResponse({ ...metaResponse, industries: metaResponse.industries.slice(0, 6) }), false)
  assert.equal(isMetaResponse({
    ...metaResponse,
    dataAsOf: { ...metaResponse.dataAsOf, areaSpend: '2026-13' },
  }), false)
})

test('accepts nullable optional response fields and rejects malformed charts', () => {
  assert.equal(isChatResponse(chatResponse), true)
  assert.equal(isChatResponse({
    ...chatResponse,
    charts: [{ ...chatResponse.charts[0], datasets: [{ label: 'x', data: ['not a number'] }] }],
  }), false)
  assert.equal(isChatResponse({ ...chatResponse, askFor: 'UNKNOWN' }), false)
  assert.equal(isChatResponse({
    ...chatResponse,
    type: 'ASK_BACK',
    sections: [{ id: 'advice', title: '안내', text: '정보를 더 주세요.' }],
  }), false)
})
