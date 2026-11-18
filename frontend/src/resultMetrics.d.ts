import type { ChatChart } from './types'

export interface KeyMetric {
  label: string
  value: string
  unit: string
  note: string
}

export function keyMetrics(charts: ChatChart[]): KeyMetric[]

export function scopeLabel(scope: string): string
