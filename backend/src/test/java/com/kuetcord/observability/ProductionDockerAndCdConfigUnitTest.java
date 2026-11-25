package com.kuetcord.observability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionDockerAndCdConfigUnitTest {

    private Path resolveRepoRoot() {
        Path cwd = Path.of("").toAbsolutePath();
        return cwd.getFileName().toString().equals("backend") ? cwd.getParent() : cwd;
    }

    @Test
    @DisplayName("Backend Dockerfile enforces fail-fast build, non-root user, healthcheck, and Render 512MB JVM flags")
    void backendDockerfileShouldBeHardenedFor512MbFreeTier() throws Exception {
        Path dockerfile = resolveRepoRoot().resolve("backend/Dockerfile");
        assertTrue(Files.exists(dockerfile), "backend/Dockerfile must exist");

        String content = Files.readString(dockerfile);
        assertFalse(content.contains("touch target/"), "Must not mask Maven build failures with placeholder JARs");
        assertFalse(content.contains("|| mkdir"), "Must not mask Maven build failures");
        assertTrue(content.contains("USER spring:spring"), "Must run as non-root user");
        assertTrue(content.contains("--chown=spring:spring"), "Artifact must be owned by non-root user");
        assertTrue(content.contains("HEALTHCHECK"), "Must define container HEALTHCHECK");
        assertTrue(content.contains("/actuator/health"), "Healthcheck must probe /actuator/health");

        // Render 512MB RAM Free-Tier JVM flags ($0.00 cost constraint)
        assertTrue(content.contains("-XX:+UseContainerSupport"));
        assertTrue(content.contains("-XX:MaxRAMPercentage=75.0"));
        assertTrue(content.contains("-XX:+UseSerialGC"));
        assertTrue(content.contains("-XX:MaxMetaspaceSize=128m"));
        assertTrue(content.contains("-XX:ReservedCodeCacheSize=48m"));
        assertTrue(content.contains("-Xss512k"));
    }

    @Test
    @DisplayName("Frontend Dockerfile & Nginx config enforce fail-fast Vite build, SPA routing, and /api + /ws proxy")
    void frontendDockerfileAndNginxShouldBeHardened() throws Exception {
        Path root = resolveRepoRoot();
        Path dockerfile = root.resolve("frontend/Dockerfile");
        Path nginxConf = root.resolve("frontend/nginx.conf");

        assertTrue(Files.exists(dockerfile), "frontend/Dockerfile must exist");
        assertTrue(Files.exists(nginxConf), "frontend/nginx.conf must exist");

        String dockerContent = Files.readString(dockerfile);
        assertFalse(dockerContent.contains("|| true"), "Must not swallow npm ci errors");
        assertFalse(dockerContent.contains("|| mkdir"), "Must not mask Vite build failures");
        assertTrue(dockerContent.contains("COPY nginx.conf /etc/nginx/conf.d/default.conf"));
        assertTrue(dockerContent.contains("HEALTHCHECK"));

        String nginxContent = Files.readString(nginxConf);
        assertFalse(nginxContent.contains("$$uri"), "Nginx config must use $uri, not literal $$uri");
        assertTrue(nginxContent.contains("try_files $uri $uri/ /index.html;"), "SPA fallback must be configured");
        assertTrue(nginxContent.contains("location /api/"), "Nginx must proxy /api/ to backend");
        assertTrue(nginxContent.contains("proxy_pass http://backend:8080/api/;"));
        assertTrue(nginxContent.contains("location /ws"), "Nginx must proxy /ws WebSocket upgrades");
        assertTrue(nginxContent.contains("proxy_set_header Upgrade $http_upgrade;"));
        assertTrue(nginxContent.contains("location /actuator/health"));
    }

    @Test
    @DisplayName("k6 CT Hub load test enforces p95 < 150ms, <1% error rate, and authenticated read paths")
    void k6CtReadScriptShouldEnforceSlaThresholds() throws Exception {
        Path k6Script = resolveRepoRoot().resolve("tests/load/k6_ct_read.js");
        assertTrue(Files.exists(k6Script), "tests/load/k6_ct_read.js must exist");

        String content = Files.readString(k6Script);
        assertTrue(content.contains("p(95)<150"), "k6 threshold must enforce p95 < 150ms");
        assertTrue(content.contains("rate<0.01"), "k6 threshold must enforce error rate < 1%");
        assertTrue(content.contains("export function setup()"), "k6 script must authenticate in setup()");
        assertTrue(content.contains("/ct/events"), "k6 script must load test /ct/events");
        assertTrue(content.contains("/ct/courses"), "k6 script must load test /ct/courses");
        assertFalse(content.contains("mock-jwt-token"), "k6 script must not hardcode a fake JWT");
    }

    @Test
    @DisplayName("CI and CD workflows validate real Docker builds and fail-fast deployment smoke checks")
    void ciAndCdWorkflowsShouldBeHardened() throws Exception {
        Path root = resolveRepoRoot();
        String ciContent = Files.readString(root.resolve(".github/workflows/ci.yml"));
        String cdContent = Files.readString(root.resolve(".github/workflows/deploy.yml"));

        assertTrue(ciContent.contains("docker compose config --quiet"));
        assertTrue(ciContent.contains("docker build -t kuetcord-backend:ci ./backend"));
        assertTrue(ciContent.contains("docker build -t kuetcord-frontend:ci ./frontend"));
        assertFalse(ciContent.contains("|| echo \"Docker compose valid\""));

        assertFalse(cdContent.contains("|| echo \"Deploy hook triggered\""));
        assertTrue(cdContent.contains("working-directory: frontend"));
        assertTrue(cdContent.contains("/actuator/health"));
        assertTrue(cdContent.contains("/actuator/prometheus"));
    }
}
