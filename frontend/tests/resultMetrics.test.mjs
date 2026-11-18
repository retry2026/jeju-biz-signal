import assert from 'node:assert/strict'
import test from 'node:test'
import { chartUnit, keyMetrics } from '../src/resultMetrics.js'

const chart = (id, data) => ({ id, type: 'bar', title: '', labels: [], datasets: [{ label: '', data }], unit: '', source: '', scope: 'AREA' })

test('shows chart values as they are without computing new numbers', () => {
  const metrics = keyMetrics([
    chart('customerMix', [62.5, 29.5, 8.0]),
    chart('competition', [180, 69.2]),
    chart('industrySeason', [100, 120]),
  ])

  assert.deepEqual(metrics.map((m) => [m.label, m.value, m.unit, m.note]), [
    ['도민 결제 비중', '62.5', '%', '동네 음식점업 전체'],
    ['같은 업종 점포', '180', '곳', '제주 동 평균 69.2곳'],
  ])
})

test('skips charts whose value is missing and keeps at most three tiles', () => {
  assert.deepEqual(keyMetrics([chart('customerMix', [null, 1, 2])]), [])
  const many = keyMetrics([
    chart('customerMix', [1]), chart('competition', [2]), chart('myStoreVsArea', [-15.2, -3.5]), chart('ticketSize', [9000, 12000]),
  ])
  assert.equal(many.length, 3)
  assert.equal(many[2].note, '동네 외식 -3.5%')
})

test('does not show a monthly sales line as year-over-year change', () => {
  const line = { ...chart('myStoreVsArea', [2000000, 4000000]), type: 'line', unit: '원' }
  assert.deepEqual(keyMetrics([line]), [])
})

test('shows large won amounts in ten-thousand won and keeps small ones', () => {
  const monthly = { ...chart('areaMonthly', [123_456_789, null]), unit: '원' }
  const { unit, scale } = chartUnit(monthly)
  assert.equal(unit, '만원')
  assert.deepEqual(monthly.datasets[0].data.map(scale), [12346, null])

  const ticket = { ...chart('ticketSize', [29_613, 33_865]), unit: '원' }
  assert.equal(chartUnit(ticket).unit, '원')
  assert.deepEqual(ticket.datasets[0].data.map(chartUnit(ticket).scale), [29_613, 33_865])
})
