import { useEffect, useRef, useState } from 'react'

const IDLE_TIMEOUT_MS = 5 * 60 * 1000
const WARNING_SECONDS = 10
const activityEvents: (keyof WindowEventMap)[] = ['pointerdown', 'keydown', 'touchstart', 'input']

/**
 * 키오스크에서 다음 사람이 앞 사람의 조건과 대화를 보지 않도록, 입력이 없으면 처음 화면으로 돌린다.
 * 돌아가기 10초 전부터 남은 초를 돌려주고, 그동안 화면을 건드리면 타이머가 다시 시작된다.
 */
export function useIdleReset(onIdle: () => void, enabled: boolean): number | null {
  const callbackRef = useRef(onIdle)
  const [secondsLeft, setSecondsLeft] = useState<number | null>(null)

  useEffect(() => {
    callbackRef.current = onIdle
  }, [onIdle])

  useEffect(() => {
    if (!enabled) return

    let warningId = 0
    let tickId = 0
    const stop = () => {
      window.clearTimeout(warningId)
      window.clearInterval(tickId)
    }
    const startCountdown = () => {
      let left = WARNING_SECONDS
      setSecondsLeft(left)
      tickId = window.setInterval(() => {
        left -= 1
        if (left > 0) {
          setSecondsLeft(left)
          return
        }
        window.clearInterval(tickId)
        setSecondsLeft(null)
        callbackRef.current()
      }, 1000)
    }
    const schedule = () => {
      warningId = window.setTimeout(startCountdown, IDLE_TIMEOUT_MS - WARNING_SECONDS * 1000)
    }
    const resetTimer = () => {
      stop()
      setSecondsLeft(null)
      schedule()
    }

    activityEvents.forEach((eventName) => window.addEventListener(eventName, resetTimer, { passive: true }))
    schedule()

    return () => {
      stop()
      setSecondsLeft(null)
      activityEvents.forEach((eventName) => window.removeEventListener(eventName, resetTimer))
    }
  }, [enabled])

  return secondsLeft
}
