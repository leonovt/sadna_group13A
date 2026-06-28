package com.sadna.group13a.infrastructure.initstate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Issue #369 — the shipped initial-state config files must describe SEATED zones using the
 * new venue-map {@code rows}/{@code columns} grid options (not a flat {@code capacity}), so the
 * seat map renders as a proper grid. These tests parse the real config files and guard against
 * a regression to the old capacity-only seated-zone format.
 */
@DisplayName("Shipped config files — venue-map row/column format (#369)")
class ConfigFileVenueMapTest {

    private final InitialStateParser parser = new InitialStateParser();

    @Test
    @DisplayName("config-example.json defines its SEATED zone with rows and columns")
    void configExample_seatedZonesUseRowsAndColumns() throws Exception {
        Path file = Path.of("config-example.json");
        assertTrue(Files.exists(file), "config-example.json must exist at the project root");

        List<InitOperation> ops;
        try (InputStream in = Files.newInputStream(file)) {
            ops = parser.parse(in);
        }

        assertTrue(seatedZoneCount(ops) > 0, "config-example.json should contain at least one SEATED zone to be meaningful");
        assertAllSeatedZonesUseGrid(ops, "config-example.json");
    }

    @Test
    @DisplayName("init-state.sample.json defines its SEATED zone with rows and columns")
    void initStateSample_seatedZonesUseRowsAndColumns() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/init-state.sample.json")) {
            assertNotNull(in, "init-state.sample.json must be on the classpath");
            List<InitOperation> ops = parser.parse(in);
            assertTrue(seatedZoneCount(ops) > 0, "init-state.sample.json should contain at least one SEATED zone");
            assertAllSeatedZonesUseGrid(ops, "init-state.sample.json");
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void assertAllSeatedZonesUseGrid(List<InitOperation> ops, String fileName) {
        for (InitOperation op : ops) {
            if (!"create-venue-map".equals(op.action())) {
                continue;
            }
            Object zonesRaw = op.args().get("zones");
            assertInstanceOf(List.class, zonesRaw, fileName + ": 'zones' must be a JSON array");
            for (Map<String, Object> zone : (List<Map<String, Object>>) zonesRaw) {
                if (!"SEATED".equalsIgnoreCase(String.valueOf(zone.get("type")))) {
                    continue;
                }
                String label = fileName + " zone '" + zone.get("name") + "'";
                assertTrue(zone.containsKey("rows"), label + " must declare 'rows'");
                assertTrue(zone.containsKey("columns"), label + " must declare 'columns'");
                assertTrue(((Number) zone.get("rows")).intValue() > 0, label + " must have rows > 0");
                assertTrue(((Number) zone.get("columns")).intValue() > 0, label + " must have columns > 0");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private long seatedZoneCount(List<InitOperation> ops) {
        return ops.stream()
                .filter(op -> "create-venue-map".equals(op.action()))
                .map(op -> op.args().get("zones"))
                .filter(List.class::isInstance)
                .flatMap(zones -> ((List<Map<String, Object>>) zones).stream())
                .filter(zone -> "SEATED".equalsIgnoreCase(String.valueOf(zone.get("type"))))
                .count();
    }
}
