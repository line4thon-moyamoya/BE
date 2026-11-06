package com.moyamoya.domain.word.util;

import com.moyamoya.domain.word.entity.HeardFrom;

import java.util.Locale;

public final class HeardFromMapper {

    private HeardFromMapper() {
    }

    public static HeardFrom fromCsv(String value) {
        if (value == null || value.isBlank()) {
            return HeardFrom.UNKNOWN;
        }

        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "parent", "parents" -> HeardFrom.PARENTS;
            case "kindergarten" -> HeardFrom.KINDERGARTEN;
            case "book" -> HeardFrom.BOOK;
            case "tv", "tv_youtube" -> HeardFrom.TV_YOUTUBE;
            case "unknown" -> HeardFrom.UNKNOWN;
            default -> throw new IllegalArgumentException("지원하지 않는 heard_from 값: " + value);
        };
    }

    public static boolean matches(HeardFrom selected, String csvValue) {
        if (selected == null || selected == HeardFrom.UNKNOWN) {
            return false;
        }

        HeardFrom csvSource = fromCsv(csvValue);
        return csvSource != HeardFrom.UNKNOWN && csvSource == selected;
    }
}
