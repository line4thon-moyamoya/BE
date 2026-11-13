package com.moyamoya.domain.question.model;

import java.util.List;

public record ExternalWordCandidate(
        String word,
        List<String> definitions
) {
    public ExternalWordCandidate {
        definitions = definitions == null ? List.of() : List.copyOf(definitions);
    }
}
