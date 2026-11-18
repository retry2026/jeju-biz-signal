import type { UserType } from '../api'

interface StartPageProps {
  onSelect: (userType: UserType) => void
}

/** 첫 화면에서 무엇을 알려주는 서비스인지 설명한다. 숫자는 질문한 뒤 실제 데이터로만 보여준다. */
const previews = [
  { no: '01', title: '손님 구성', text: '동네 외식 결제가 도민, 내국인 관광객, 외국인 관광객 중 누구에게서 나오는지' },
  { no: '02', title: '계절과 관광 흐름', text: '업종이 몇 월에 강하고 약한지, 최근 4주 입도객이 작년보다 늘었는지' },
  { no: '03', title: '경쟁 점포', text: '같은 업종 가게가 동네에 몇 곳 있고 제주 평균과 비교하면 어떤지' },
]

export function StartPage({ onSelect }: StartPageProps) {
  return (
    <section className="start" aria-labelledby="start-title">
      <div className="hero">
        <div className="hero-copy">
          <p className="hero-kicker">제주 외식업 상권 분석 · 창업과 가게 운영</p>
          <h1 id="start-title">우리 동네 손님,<br /><em>도민</em>일까<br />관광객일까</h1>
          <p className="hero-lead">손님 구성, 계절과 관광 흐름, 경쟁 점포를 공개 데이터로 계산해 창업과 가게 운영 판단을 돕습니다.</p>
        </div>
        <ol className="hero-preview" aria-label="알 수 있는 것">
          {previews.map((item) => (
            <li key={item.no}>
              <span className="preview-no" aria-hidden="true">{item.no}</span>
              <span>
                <strong>{item.title}</strong>
                <span className="preview-text">{item.text}</span>
              </span>
            </li>
          ))}
        </ol>
      </div>
      <div className="start-choices">
        <button className="start-choice is-primary" type="button" onClick={() => onSelect('STARTUP')}>
          <span className="choice-kind">예비 창업자</span>
          <span className="choice-title">창업을 준비하고 있어요</span>
          <span className="choice-foot">후보 동네의 손님과 경쟁을 봅니다<span aria-hidden="true">→</span></span>
        </button>
        <button className="start-choice" type="button" onClick={() => onSelect('EXISTING')}>
          <span className="choice-kind">기존 사업자</span>
          <span className="choice-title">가게를 운영하고 있어요</span>
          <span className="choice-foot">계절 흐름과 내 매출을 비교합니다<span aria-hidden="true">→</span></span>
        </button>
      </div>
    </section>
  )
}
