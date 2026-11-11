package com.moyamoya.domain.question.external;

public record DictionaryEntry(
        String word,
        String definition,
        String dictionarySource,
        String dictionaryEntryId
) {
}
