import { useEffect, useRef } from 'react'

const IDLE_TIMEOUT_MS = 2 * 60 * 1000
const activityEvents: (keyof WindowEventMap)[] = ['pointerdown', 'keydown', 'touchstart', 'input']

export function useIdleReset(onIdle: () => void) {
  const callbackRef = useRef(onIdle)

  useEffect(() => {
    callbackRef.current = onIdle
  }, [onIdle])

  useEffect(() => {
    let timeoutId = 0
    const resetTimer = () => {
      window.clearTimeout(timeoutId)
      timeoutId = window.setTimeout(() => callbackRef.current(), IDLE_TIMEOUT_MS)
    }

    activityEvents.forEach((eventName) => window.addEventListener(eventName, resetTimer, { passive: true }))
    resetTimer()

    return () => {
      window.clearTimeout(timeoutId)
      activityEvents.forEach((eventName) => window.removeEventListener(eventName, resetTimer))
    }
  }, [])
}
