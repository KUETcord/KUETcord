package com.kuetcord;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class KuetcordApplicationSmokeUnitTest {

    @Test
    @DisplayName("Smoke Test: KuetcordApplication class loads cleanly")
    void applicationClassLoads() {
        KuetcordApplication app = new KuetcordApplication();
        assertNotNull(app, "KuetcordApplication instance should not be null");
    }
}
