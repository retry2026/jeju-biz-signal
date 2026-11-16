const isRecord = (value) => typeof value === 'object' && value !== null && !Array.isArray(value)
const isString = (value) => typeof value === 'string'
const isStringArray = (value) => Array.isArray(value) && value.every(isString)
const isOneOf = (value, values) => values.includes(value)

function isMonth(value) {
  return typeof value === 'string' && /^\d{4}-(0[1-9]|1[0-2])$/.test(value)
}

function isDate(value) {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false
  const parsed = new Date(`${value}T00:00:00.000Z`)
  return !Number.isNaN(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value
}

function isArea(value) {
  return isRecord(value)
    && typeof value.code === 'string'
    && /^\d{8}$/.test(value.code)
    && isString(value.name)
    && isOneOf(value.city, ['제주시', '서귀포시'])
}

function isIndustry(value) {
  return isRecord(value)
    && isOneOf(value.code, ['CAFE', 'KOREAN', 'CHINESE', 'JAPANESE', 'WESTERN', 'PUB', 'FASTFOOD'])
    && isString(value.name)
}

function isDataAsOf(value) {
  return isRecord(value)
    && isDate(value.store)
    && isMonth(value.areaSpend)
    && isMonth(value.industrySpend)
    && isDate(value.arrivals)
}

export function isMetaResponse(value) {
  return isRecord(value)
    && Array.isArray(value.areas)
    && value.areas.length === 43
    && value.areas.every(isArea)
    && Array.isArray(value.industries)
    && value.industries.length === 7
    && value.industries.every(isIndustry)
    && isRecord(value.exampleQuestions)
    && isStringArray(value.exampleQuestions.STARTUP)
    && isStringArray(value.exampleQuestions.EXISTING)
    && isDataAsOf(value.dataAsOf)
}

function isSection(value) {
  return isRecord(value)
    && isString(value.id)
    && isString(value.title)
    && isString(value.text)
}

function isDataset(value) {
  return isRecord(value)
    && isString(value.label)
    && Array.isArray(value.data)
    && value.data.every((point) => point === null || (typeof point === 'number' && Number.isFinite(point)))
    && (value.dashed === undefined || value.dashed === null || typeof value.dashed === 'boolean')
}

function isChart(value) {
  return isRecord(value)
    && isString(value.id)
    && isOneOf(value.type, ['doughnut', 'bar', 'line'])
    && isOneOf(value.id, [
      'customerMix',
      'areaMonthly',
      'ticketSize',
      'touristFlow',
      'industrySeason',
      'competition',
      'myStoreVsArea',
    ])
    && isString(value.title)
    && isStringArray(value.labels)
    && Array.isArray(value.datasets)
    && value.datasets.every(isDataset)
    && value.datasets.every((dataset) => dataset.data.length === value.labels.length)
    && isString(value.unit)
    && isString(value.source)
    && isOneOf(value.scope, ['AREA', 'RADIUS', 'JEJU'])
}

function isEvidence(value) {
  return isRecord(value)
    && isString(value.tool)
    && isOneOf(value.tool, ['competition', 'areaCustomers', 'industryTourism'])
    && isString(value.source)
    && isString(value.period)
    && isOneOf(value.scope, ['AREA', 'RADIUS', 'JEJU'])
    && typeof value.lowSample === 'boolean'
}

export function isChatResponse(value) {
  return isRecord(value)
    && isOneOf(value.type, ['ANSWER', 'REFUSE', 'ASK_BACK'])
    && isString(value.summary)
    && Array.isArray(value.sections)
    && value.sections.every(isSection)
    && Array.isArray(value.charts)
    && value.charts.length <= 3
    && value.charts.every(isChart)
    && Array.isArray(value.evidence)
    && value.evidence.every(isEvidence)
    && isStringArray(value.limits)
    && isStringArray(value.suggestions)
    && value.suggestions.length <= 3
    && (value.type !== 'ASK_BACK' || value.sections.length === 0)
    && (value.askFor === undefined
      || value.askFor === null
      || isOneOf(value.askFor, ['AREA', 'INDUSTRY', 'MESSAGE', 'MY_STORE']))
}
