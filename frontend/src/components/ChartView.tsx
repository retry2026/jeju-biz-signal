import {
  ArcElement,
  BarElement,
  CategoryScale,
  Chart as ChartJS,
  Legend,
  LinearScale,
  LineElement,
  PointElement,
  Tooltip,
  type ChartData,
} from 'chart.js'
import { Bar, Doughnut, Line } from 'react-chartjs-2'
import type { ChatChart } from '../api'
import { scopeLabel } from '../resultMetrics.js'

ChartJS.register(ArcElement, BarElement, CategoryScale, LinearScale, LineElement, PointElement, Tooltip, Legend)

interface ChartViewProps {
  chart: ChatChart
}

// 손님 구성 색은 모든 화면에서 같은 뜻: 도민 남청, 내국인 관광객 감귤, 외국인 관광객 회색.
const SEA = '#0f4c6e'
const TANGERINE = '#e2702a'
const GRAY = '#8f9891'

ChartJS.defaults.font.family = "Pretendard, system-ui, 'Malgun Gothic', sans-serif"
ChartJS.defaults.font.size = 14
ChartJS.defaults.color = '#5f6560'

const common = { responsive: true, maintainAspectRatio: false, animation: false as const }
const grid = {
  x: { grid: { display: false } },
  y: { grid: { color: '#e6e7e2' }, border: { display: false } },
}

export function ChartView({ chart }: ChartViewProps) {
  const lineColors = [SEA, TANGERINE, GRAY]
  const data = {
    labels: chart.labels,
    datasets: chart.datasets.map((dataset, index) => {
      const color = dataset.dashed ? GRAY : lineColors[index % lineColors.length]
      return {
        label: dataset.label,
        data: dataset.data,
        borderColor: chart.type === 'doughnut' ? '#ffffff' : color,
        backgroundColor: chart.type === 'doughnut'
          ? [SEA, TANGERINE, GRAY]
          : chart.type === 'bar' ? [SEA, GRAY, TANGERINE] : color,
        borderDash: dataset.dashed ? [6, 4] : undefined,
        borderWidth: chart.type === 'line' ? 2.5 : chart.type === 'doughnut' ? 2 : 0,
        borderRadius: chart.type === 'bar' ? 3 : 0,
        pointRadius: chart.type === 'line' ? 2 : 0,
        tension: 0.2,
        spanGaps: false,
      }
    }),
  } as ChartData<'bar' | 'line' | 'doughnut', (number | null)[], string>

  return (
    <figure className="chart">
      <figcaption>{chart.title}</figcaption>
      <div className={`chart-canvas chart-${chart.type}`}>
        {chart.type === 'line' && <Line data={data as ChartData<'line', (number | null)[], string>} options={{ ...common, scales: grid }} />}
        {chart.type === 'bar' && <Bar data={data as ChartData<'bar', (number | null)[], string>} options={{ ...common, scales: grid, plugins: { legend: { display: chart.datasets.length > 1 } } }} />}
        {chart.type === 'doughnut' && <Doughnut data={data as ChartData<'doughnut', (number | null)[], string>} options={{ ...common, cutout: '62%', plugins: { legend: { position: 'right' } } }} />}
      </div>
      <p className="chart-source">단위 {chart.unit} · {chart.source} · {scopeLabel(chart.scope)}</p>
    </figure>
  )
}
