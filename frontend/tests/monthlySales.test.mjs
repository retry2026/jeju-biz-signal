import assert from 'node:assert/strict'
import test from 'node:test'
import { parseMonthlySales } from '../src/monthlySales.js'

test('parses complete monthly sales and ignores empty rows', () => {
  assert.deepEqual(parseMonthlySales([
    { month: '2025-01', amount: '120000' },
    { month: '', amount: '' },
  ]), {
    monthlySales: [{ month: '2025-01', amount: 120000 }],
    error: null,
  })
})

test('rejects incomplete or invalid monthly sales entries', () => {
  assert.equal(parseMonthlySales([{ month: '2025-01', amount: '' }]).error, 'INCOMPLETE')
  assert.equal(parseMonthlySales([{ month: '2025-13', amount: '1000' }]).error, 'INVALID_MONTH')
  assert.equal(parseMonthlySales([{ month: '2025-01', amount: '1.5' }]).error, 'INVALID_AMOUNT')
  assert.equal(parseMonthlySales([{ month: '2025-01', amount: '9007199254740992' }]).error, 'INVALID_AMOUNT')
})

test('rejects duplicate months and more than twelve entries', () => {
  assert.equal(parseMonthlySales([
    { month: '2025-01', amount: '1000' },
    { month: '2025-01', amount: '2000' },
  ]).error, 'DUPLICATE_MONTH')

  const thirteenMonths = Array.from({ length: 13 }, (_, index) => ({
    month: `2025-${String(index + 1).padStart(2, '0')}`,
    amount: '1000',
  }))
  assert.equal(parseMonthlySales(thirteenMonths).error, 'TOO_MANY')
})
