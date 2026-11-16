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

ChartJS.register(ArcElement, BarElement, CategoryScale, LinearScale, LineElement, PointElement, Tooltip, Legend)

interface ChartViewProps {
  chart: ChatChart
}

export function ChartView({ chart }: ChartViewProps) {
  const data = {
    labels: chart.labels,
    datasets: chart.datasets.map((dataset, index) => ({
      label: dataset.label,
      data: dataset.data,
      borderColor: index === 0 ? '#087f72' : '#d67828',
      backgroundColor: chart.type === 'doughnut'
        ? ['#087f72', '#e7a349', '#617db2', '#b85d6d']
        : index === 0 ? 'rgba(8, 127, 114, 0.22)' : 'rgba(214, 120, 40, 0.18)',
      borderDash: dataset.dashed ? [7, 5] : undefined,
      borderWidth: 2,
      tension: 0.25,
      spanGaps: false,
    })),
  } as ChartData<'bar' | 'line' | 'doughnut', (number | null)[], string>

  return (
    <figure className="chart-card">
      <figcaption>{chart.title}</figcaption>
      <div className="chart-canvas">
        {chart.type === 'line' && <Line data={data as ChartData<'line', (number | null)[], string>} options={{ responsive: true, maintainAspectRatio: false }} />}
        {chart.type === 'bar' && <Bar data={data as ChartData<'bar', (number | null)[], string>} options={{ responsive: true, maintainAspectRatio: false }} />}
        {chart.type === 'doughnut' && <Doughnut data={data as ChartData<'doughnut', (number | null)[], string>} options={{ responsive: true, maintainAspectRatio: false }} />}
      </div>
      <p className="chart-source">{chart.unit} · {chart.source} · {chart.scope}</p>
    </figure>
  )
}
