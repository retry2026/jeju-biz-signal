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
  // 동네가 더 많은 제주시를 먼저 보여준다 (가나다순이면 서귀포시가 먼저 온다).
  const cities = [...new Set(areas.map((area) => area.city))]
    .sort((a, b) => Number(b === '제주시') - Number(a === '제주시'))
  const areaName = areas.find((area) => area.code === selectedArea)?.name
  const industryName = industries.find((industry) => industry.code === selectedIndustry)?.name

  return (
    <section className="conditions" aria-labelledby="condition-title">
      <div className="conditions-areas">
        <h2 id="condition-title">어느 동네인가요?</h2>
        <p className="page-sub">읍·면·동 하나를 눌러 주세요.</p>
        {cities.map((city) => (
          <fieldset className="city-group" key={city}>
            <legend>{city}</legend>
            <div className="area-grid">
              {areas.filter((area) => area.city === city).map((area) => (
                <button
                  aria-pressed={selectedArea === area.code}
                  className={`area-button${selectedArea === area.code ? ' is-selected' : ''}`}
                  key={area.code}
                  onClick={() => onAreaChange(area.code)}
                  type="button"
                >
                  {area.name}
                </button>
              ))}
            </div>
          </fieldset>
        ))}
      </div>

      <aside className="conditions-side">
        <fieldset className="industry-group">
          <legend>업종</legend>
          <div className="industry-list">
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
        <button
          className="go-button"
          disabled={!selectedArea || !selectedIndustry}
          onClick={onContinue}
          type="button"
        >
          질문하러 가기
          <span>{areaName && industryName ? `${areaName} · ${industryName}` : '동네와 업종을 골라 주세요'}</span>
        </button>
      </aside>
    </section>
  )
}
