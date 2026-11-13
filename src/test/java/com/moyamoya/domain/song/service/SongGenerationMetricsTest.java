package com.moyamoya.domain.song.service;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SongGenerationMetricsTest {
    @Test
    void failedStagePreservesOriginalExceptionAndZeroesUnstartedStages() {
        var metrics = new SongGenerationMetrics();
        var error = new IllegalStateException("download failed");
        assertThatThrownBy(() -> metrics.measure(SongGenerationMetrics.Stage.BACKING, () -> { throw error; }))
                .isSameAs(error);
        assertThat(metrics.summary()).contains("failedStage=BACKING", "ttsCalls=0", "ttsMs=0",
                "mixingMs=0", "uploadMs=0");
    }

    @Test
    void separateMixesHaveIndependentCountersAndIdentifiers() {
        var first = new SongGenerationMetrics();
        var second = new SongGenerationMetrics();
        first.inputStarted();
        first.ttsStarted(1);
        first.ttsStarted(2);
        assertThat(first.summary()).contains("inputs=1", "ttsCalls=2", "qualityRetries=1");
        assertThat(second.summary()).contains("inputs=0", "ttsCalls=0", "qualityRetries=0");
        assertThat(first.summary().split(",")[0]).isNotEqualTo(second.summary().split(",")[0]);
    }
}
