// 답변 상단 숫자 타일. 응답 차트에 있는 값을 그대로 꺼내기만 한다.
// 화면에서 새 숫자를 계산하지 않는다 (나누기, 더하기, 최댓값 찾기 없음).

const format = (value, digits) => value.toLocaleString('ko-KR', { maximumFractionDigits: digits })
const isNumber = (value) => typeof value === 'number' && Number.isFinite(value)

const pickers = {
  customerMix: (chart) => {
    const value = chart.datasets[0]?.data[0]
    return isNumber(value) && {
      label: '도민 결제 비중',
      value: format(value, 1),
      unit: '%',
      note: '동네 음식점업 전체',
    }
  },
  competition: (chart) => {
    const [store, average] = chart.datasets[0]?.data ?? []
    return isNumber(store) && {
      label: '같은 업종 점포',
      value: format(store, 0),
      unit: '곳',
      note: isNumber(average) ? `제주 동 평균 ${format(average, 1)}곳` : '동네 기준',
    }
  },
  myStoreVsArea: (chart) => {
    const [mine, area] = chart.datasets[0]?.data ?? []
    return isNumber(mine) && {
      label: '내 가게 전년 대비',
      value: format(mine, 1),
      unit: '%',
      note: isNumber(area) ? `동네 외식 ${format(area, 1)}%` : '동네 비교 불가',
    }
  },
  ticketSize: (chart) => {
    const [resident, tourist] = chart.datasets[0]?.data ?? []
    return isNumber(tourist) && {
      label: '관광객 건당 결제액',
      value: format(tourist, 0),
      unit: '원',
      note: isNumber(resident) ? `도민 ${format(resident, 0)}원` : '동네 음식점업 전체',
    }
  },
}

/** 차트 목록에서 숫자 타일을 최대 3개 고른다. 고를 수 있는 차트가 없으면 빈 목록. */
export function keyMetrics(charts) {
  return charts
    .map((chart) => pickers[chart.id]?.(chart))
    .filter(Boolean)
    .slice(0, 3)
}

/** 데이터 범위 코드를 화면 문구로 바꾼다. */
export function scopeLabel(scope) {
  return scope === 'JEJU' ? '제주 전체' : scope === 'RADIUS' ? '반경 내' : '동네'
}
