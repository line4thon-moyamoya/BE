package com.moyamoya.domain.song.service;

import com.moyamoya.domain.child.entity.Child;
import com.moyamoya.domain.child.repository.ChildRepository;
import com.moyamoya.domain.song.dto.response.SongListResponse;
import com.moyamoya.domain.song.dto.response.SongDetailResponse;
import com.moyamoya.domain.song.dto.response.SongWordResponse;
import com.moyamoya.domain.song.dto.response.SongSummaryResponse;
import com.moyamoya.domain.song.entity.SongStatus;
import com.moyamoya.domain.song.exception.SongErrorCode;
import com.moyamoya.domain.song.repository.SongRepository;
import com.moyamoya.domain.song.repository.SongWordRepository;
import com.moyamoya.global.exception.CustomException;
import com.moyamoya.global.s3.S3Service;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SongService {
    private final ChildRepository childRepository;
    private final SongRepository songRepository;
    private final SongWordRepository songWordRepository;
    private final S3Service S3Service;

    @Transactional(readOnly = true)
    public SongListResponse getSongs(Long userId, Long childId) {
        validateChildOwnership(userId, childId);
        var songs = songRepository.findAllByChild_IdOrderByCreatedAtDescIdDesc(childId);
        if (songs.isEmpty()) {
            return new SongListResponse(List.of());
        }
        var wordsBySong = songWordRepository.findAllWithWordByChildId(childId).stream()
                .collect(Collectors.groupingBy(sw -> sw.getSong().getId(),
                        Collectors.mapping(sw -> sw.getLearnedWord().getWord().getWord(), Collectors.toList())));
        return new SongListResponse(songs.stream()
                .map(song -> new SongSummaryResponse(song.getId(), song.getTitle(),
                        wordsBySong.getOrDefault(song.getId(), List.of())))
                .toList());
    }

    @Transactional(readOnly = true)
    public SongDetailResponse getSongDetail(Long userId, Long childId, Long songId) {
        if (songId == null || songId <= 0) {
            throw new CustomException(SongErrorCode.INVALID_SONG_ID);
        }
        Child child = validateChildOwnership(userId, childId);
        var song = songRepository.findByIdAndChild_Id(songId, childId)
                .orElseThrow(() -> new CustomException(SongErrorCode.SONG_NOT_FOUND));
        if (song.getStatus() != SongStatus.READY) {
            return new SongDetailResponse(song.getId(), song.getTitle(), song.getStatus(), null, List.of());
        }
        var words = songWordRepository.findAllBySong_IdOrderByWordOrderAsc(songId).stream()
                .map(sw -> SongWordResponse.from(sw.getLearnedWord().getWord(), child.getAgeGroup()))
                .toList();
        return new SongDetailResponse(song.getId(), song.getTitle(), song.getStatus(),
                S3Service.getPresignedUrl(song.getAudioUrl()), words);
    }

    private Child validateChildOwnership(Long userId, Long childId) {
        if (childId == null || childId <= 0) {
            throw new CustomException(SongErrorCode.INVALID_CHILD_ID);
        }
        Child child = childRepository.findById(childId)
                .orElseThrow(() -> new CustomException(SongErrorCode.CHILD_NOT_FOUND));
        if (!child.getUser().getId().equals(userId)) {
            throw new CustomException(SongErrorCode.CHILD_ACCESS_DENIED);
        }
        return child;
    }
}
