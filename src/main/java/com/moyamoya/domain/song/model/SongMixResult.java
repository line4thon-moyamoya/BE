package com.moyamoya.domain.song.model;

import java.nio.file.Path;

public record SongMixResult(Path outputPath, byte[] audio) {}
