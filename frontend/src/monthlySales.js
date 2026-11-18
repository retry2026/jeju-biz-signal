const monthPattern = /^\d{4}-(0[1-9]|1[0-2])$/
const amountPattern = /^\d+$/
/** 화면은 0을 덜 누르도록 만원 단위로 받고, 서버에는 원 단위로 보낸다. */
const WON_PER_UNIT = 10_000

export function parseMonthlySales(drafts) {
  const enteredRows = drafts.filter((row) => row.month.trim() !== '' || row.amount.trim() !== '')

  if (enteredRows.length > 12) {
    return { monthlySales: [], error: 'TOO_MANY' }
  }
  if (enteredRows.some((row) => row.month.trim() === '' || row.amount.trim() === '')) {
    return { monthlySales: [], error: 'INCOMPLETE' }
  }
  if (enteredRows.some((row) => !monthPattern.test(row.month))) {
    return { monthlySales: [], error: 'INVALID_MONTH' }
  }

  const months = enteredRows.map((row) => row.month)
  if (new Set(months).size !== months.length) {
    return { monthlySales: [], error: 'DUPLICATE_MONTH' }
  }

  const monthlySales = []
  for (const row of enteredRows) {
    if (!amountPattern.test(row.amount)) {
      return { monthlySales: [], error: 'INVALID_AMOUNT' }
    }

    const amount = Number(row.amount) * WON_PER_UNIT
    if (!Number.isSafeInteger(amount)) {
      return { monthlySales: [], error: 'INVALID_AMOUNT' }
    }
    monthlySales.push({ month: row.month, amount })
  }

  return { monthlySales, error: null }
}
