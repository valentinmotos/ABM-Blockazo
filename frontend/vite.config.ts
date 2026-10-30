import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const backendTarget = process.env.API_PROXY_TARGET ?? 'http://localhost:8080'
const oauthProxy = { target: backendTarget, changeOrigin: true, headers: { 'X-Forwarded-Host': 'localhost:8080', 'X-Forwarded-Proto': 'http' } }

export default defineConfig({ plugins: [react()], server: { proxy: { '/api': backendTarget, '/oauth2': oauthProxy, '/login/oauth2': oauthProxy } } })
