package com.kuetcord.observability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObservabilityConfigUnitTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Path resolveRepoRoot() {
        Path cwd = Path.of("").toAbsolutePath();
        return cwd.getFileName().toString().equals("backend") ? cwd.getParent() : cwd;
    }

    @Test
    @DisplayName("Prometheus config targets backend and native /actuator/prometheus scrape endpoints")
    void prometheusConfigShouldExistAndTargetActuator() throws Exception {
        Path promFile = resolveRepoRoot().resolve("monitoring/prometheus/prometheus.yml");
        assertTrue(Files.exists(promFile), "monitoring/prometheus/prometheus.yml must exist");

        String content = Files.readString(promFile);
        assertTrue(content.contains("metrics_path: '/actuator/prometheus'"));
        assertTrue(content.contains("targets: ['backend:8080']"));
        assertTrue(content.contains("targets: ['host.docker.internal:8080']"));
    }

    @Test
    @DisplayName("Grafana provisioning datasources and dashboards should wire Prometheus cleanly")
    void grafanaProvisioningShouldBeValid() throws Exception {
        Path root = resolveRepoRoot();
        Path dsFile = root.resolve("monitoring/grafana/provisioning/datasources/datasource.yml");
        Path dashProviderFile = root.resolve("monitoring/grafana/provisioning/dashboards/dashboard.yml");

        assertTrue(Files.exists(dsFile), "Grafana datasource provisioning file must exist");
        assertTrue(Files.exists(dashProviderFile), "Grafana dashboard provider file must exist");

        String dsContent = Files.readString(dsFile);
        assertTrue(dsContent.contains("uid: prometheus"));
        assertTrue(dsContent.contains("url: http://prometheus:9090"));

        String providerContent = Files.readString(dashProviderFile);
        assertTrue(providerContent.contains("path: /var/lib/grafana/dashboards"));
    }

    @Test
    @DisplayName("Grafana dashboard JSON parses cleanly and contains HTTP & JVM observability panels")
    void grafanaDashboardJsonShouldBeValid() throws Exception {
        Path dashJson = resolveRepoRoot().resolve("monitoring/grafana/dashboards/kuetcord-observability.json");
        assertTrue(Files.exists(dashJson), "kuetcord-observability.json dashboard must exist");

        JsonNode root = objectMapper.readTree(Files.readString(dashJson));
        assertEquals("kuetcord-observability", root.path("uid").asText());
        assertEquals("KUETcord Spring Boot Observability Dashboard", root.path("title").asText());
        assertTrue(root.path("panels").isArray() && root.path("panels").size() >= 7,
                "Dashboard must define at least 7 observability panels");
    }

    @Test
    @DisplayName("Spring application.yml enables Prometheus endpoint, application tag, and HTTP histograms")
    void applicationYmlShouldConfigurePrometheusHistograms() throws Exception {
        Path appYml = resolveRepoRoot().resolve("backend/src/main/resources/application.yml");
        String content = Files.readString(appYml);

        assertTrue(content.contains("http.server.requests: true"));
        assertTrue(content.contains("application: ${spring.application.name}"));
        assertTrue(content.contains("prometheus"));
    }
}
