import type { Area, Industry } from '../api'

interface ConditionPageProps {
  areas: Area[]
  industries: Industry[]
  selectedArea: string
  selectedIndustry: string
  onAreaChange: (code: string) => void
  onIndustryChange: (code: string) => void
  onContinue: () => void
}

export function ConditionPage({
  areas,
  industries,
  selectedArea,
  selectedIndustry,
  onAreaChange,
  onIndustryChange,
  onContinue,
}: ConditionPageProps) {
  const cities = [...new Set(areas.map((area) => area.city))]

  return (
    <section className="page-card" aria-labelledby="condition-title">
      <p className="step-label">2 / 3 · 분석 조건</p>
      <h2 id="condition-title">어느 동네, 어떤 업종인가요?</h2>
      <div className="form-field">
        <label htmlFor="area">지역</label>
        <select id="area" value={selectedArea} onChange={(event) => onAreaChange(event.target.value)}>
          <option value="">읍·면·동을 선택해 주세요</option>
          {cities.map((city) => (
            <optgroup key={city} label={city}>
              {areas.filter((area) => area.city === city).map((area) => (
                <option key={area.code} value={area.code}>{area.name}</option>
              ))}
            </optgroup>
          ))}
        </select>
      </div>
      <fieldset className="form-field">
        <legend>업종</legend>
        <div className="industry-grid">
          {industries.map((industry) => (
            <button
              aria-pressed={selectedIndustry === industry.code}
              className={`industry-button${selectedIndustry === industry.code ? ' is-selected' : ''}`}
              key={industry.code}
              onClick={() => onIndustryChange(industry.code)}
              type="button"
            >
              {industry.name}
            </button>
          ))}
        </div>
      </fieldset>
      <div className="page-actions">
        <button
          className="primary-button"
          disabled={!selectedArea || !selectedIndustry}
          onClick={onContinue}
          type="button"
        >
          질문하러 가기
        </button>
      </div>
    </section>
  )
}
