package com.bracits.easyJavaPdf.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Represents the desired orientation (rotation) for generated PDF pages.
 */
public enum PageOrientation {
    PORTRAIT(0),
    LANDSCAPE(90),
    INVERTED_PORTRAIT(180),
    SEASCAPE(270);

    private final int rotationDegrees;

    PageOrientation(int rotationDegrees) {
        this.rotationDegrees = rotationDegrees;
    }

    public int getRotationDegrees() {
        return rotationDegrees;
    }

    /**
     * Attempts to parse the provided value into a {@link PageOrientation}.
     *
     * @param value the raw value to parse
     * @return an {@link Optional} containing the parsed orientation if successful
     */
    public static Optional<PageOrientation> parse(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Optional.empty();
        }

        try {
            String normalized = value.trim().toUpperCase(Locale.ROOT);
            return Optional.of(PageOrientation.valueOf(normalized));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    /**
     * Resolves the orientation or falls back to the provided default.
     *
     * @param value          The raw orientation value.
     * @param defaultValue   The default value to use when parsing fails or input is blank.
     * @return The resolved orientation.
     */
    public static PageOrientation fromOrDefault(String value, PageOrientation defaultValue) {
        return parse(value).orElse(defaultValue);
    }

    /**
     * @return All supported orientation names for validation/error messaging.
     */
    public static Set<String> supportedNames() {
        return Arrays.stream(values())
                .map(Enum::name)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }
}
