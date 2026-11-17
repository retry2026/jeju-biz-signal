import type { UserType } from '../api'

interface StartPageProps {
  onSelect: (userType: UserType) => void
}

export function StartPage({ onSelect }: StartPageProps) {
  return (
    <section className="page-card start-page" aria-labelledby="start-title">
      <p className="step-label">1 / 3 · 사용자 유형</p>
      <h2 id="start-title">어떤 상황이신가요?</h2>
      <p className="lead">알고 싶은 동네와 업종을 고르면 공개 데이터로 살펴봅니다.</p>
      <div className="choice-grid">
        <button className="choice-card" type="button" onClick={() => onSelect('STARTUP')}>
          <span className="choice-icon" aria-hidden="true">⌂</span>
          <span className="choice-title">창업을 준비 중이에요</span>
          <span className="choice-description">후보 동네와 손님 구성이 궁금해요</span>
        </button>
        <button className="choice-card" type="button" onClick={() => onSelect('EXISTING')}>
          <span className="choice-icon" aria-hidden="true">⌁</span>
          <span className="choice-title">가게를 운영하고 있어요</span>
          <span className="choice-description">동네와 업종의 흐름을 알고 싶어요</span>
        </button>
      </div>
    </section>
  )
}
