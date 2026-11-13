import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // 개발 중 /api 요청은 Spring Boot(8080)로 넘긴다
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  build: {
    // 빌드 결과를 Spring 정적 파일 폴더에 넣어 jar 하나로 실행한다
    outDir: '../backend/src/main/resources/static',
    emptyOutDir: true,
  },
})
