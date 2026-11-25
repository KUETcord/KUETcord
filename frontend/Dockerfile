# ==============================================================================
# Multi-Stage Dockerfile for KUETcord React 18 + Vite Frontend
# Fail-Fast Production Build + Hardened Nginx Reverse Proxy
# ==============================================================================

# Stage 1: Build the production static assets
FROM node:20-alpine AS builder
WORKDIR /app

ARG VITE_API_BASE_URL=/api/v1
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL

COPY package*.json ./
RUN npm ci

COPY . .
RUN npm run build

# Stage 2: Serve via Nginx Alpine with SPA routing + /api & /ws reverse proxy
FROM nginx:alpine

COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=5s --retries=3 --start-period=10s \
  CMD wget --no-verbose --tries=1 --spider http://localhost:80/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
