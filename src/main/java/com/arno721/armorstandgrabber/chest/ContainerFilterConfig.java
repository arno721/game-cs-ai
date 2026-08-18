package com.arno721.armorstandgrabber.chest;

import java.util.List;

public record ContainerFilterConfig(
    boolean checkHandlerType,
    boolean checkTitle,
    List<String> titleWhitelist,
    List<String> titleBlacklist
) {
    private static final List<String> DEFAULT_TITLES = List.of(
        "Chest", "Large Chest", "Shulker Box", "Barrel", "Chest Minecart", "Chest Boat"
    );

    public ContainerFilterConfig {
        titleWhitelist = List.copyOf(titleWhitelist);
        titleBlacklist = List.copyOf(titleBlacklist);
    }

    public static ContainerFilterConfig defaults() {
        return new ContainerFilterConfig(true, true, DEFAULT_TITLES, List.of());
    }
}
