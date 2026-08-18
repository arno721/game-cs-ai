package com.arno721.armorstandgrabber.chest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ContainerDetectorTest {
    @Test
    void defaultTitlesAcceptReferenceNames() {
        ContainerFilterConfig config = ContainerFilterConfig.defaults();
        assertTrue(ContainerDetector.titleAllowed("Chest", config));
        assertTrue(ContainerDetector.titleAllowed("Large Chest", config));
        assertTrue(ContainerDetector.titleAllowed("Shulker Box", config));
        assertTrue(ContainerDetector.titleAllowed("Barrel", config));
        assertTrue(ContainerDetector.titleAllowed("Chest Minecart", config));
        assertTrue(ContainerDetector.titleAllowed("Chest Boat", config));
    }

    @Test
    void blacklistWinsOverWhitelist() {
        ContainerFilterConfig config = new ContainerFilterConfig(
            true, true,
            List.of("Chest", "Custom Loot"),
            List.of("Chest")
        );
        assertFalse(ContainerDetector.titleAllowed("Chest", config));
        assertTrue(ContainerDetector.titleAllowed("Custom Loot", config));
    }

    @Test
    void matchingIsWhitespaceAndCaseInsensitive() {
        ContainerFilterConfig config = ContainerFilterConfig.defaults();
        assertTrue(ContainerDetector.titleAllowed("  large   CHEST ", config));
    }
}
