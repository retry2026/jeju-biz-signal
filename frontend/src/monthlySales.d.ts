export interface MonthlySaleDraft {
  month: string
  amount: string
}

export interface MonthlySale {
  month: string
  amount: number
}

export type MonthlySalesError =
  | 'TOO_MANY'
  | 'INCOMPLETE'
  | 'INVALID_MONTH'
  | 'DUPLICATE_MONTH'
  | 'INVALID_AMOUNT'

export interface MonthlySalesParseResult {
  monthlySales: MonthlySale[]
  error: MonthlySalesError | null
}

export function parseMonthlySales(drafts: MonthlySaleDraft[]): MonthlySalesParseResult
