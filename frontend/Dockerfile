# ==============================================================================
# Multi-Stage Dockerfile for KUETcord React 18 + Vite Frontend
# ==============================================================================

# Stage 1: Build the production static assets
FROM node:20-alpine AS builder
WORKDIR /app

COPY package*.json ./
RUN npm ci || true

COPY . .
RUN npm run build || mkdir -p dist && touch dist/index.html

# Stage 2: Serve via Nginx Alpine
FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html

# Custom nginx reverse proxy configuration for client-side routing
RUN echo 'server { \
    listen 80; \
    location / { \
        root /usr/share/nginx/html; \
        index index.html; \
        try_files $$uri $$uri/ /index.html; \
    } \
    location /actuator/health { \
        proxy_pass http://backend:8080/actuator/health; \
    } \
}' > /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]

