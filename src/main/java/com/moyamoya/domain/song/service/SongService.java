package com.moyamoya.domain.song.service;

import com.moyamoya.domain.child.entity.Child;
import com.moyamoya.domain.child.repository.ChildRepository;
import com.moyamoya.domain.song.dto.response.SongListResponse;
import com.moyamoya.domain.song.dto.response.SongSummaryResponse;
import com.moyamoya.domain.song.exception.SongErrorCode;
import com.moyamoya.domain.song.repository.SongRepository;
import com.moyamoya.domain.song.repository.SongWordRepository;
import com.moyamoya.global.exception.CustomException;
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

    @Transactional(readOnly = true)
    public SongListResponse getSongs(Long userId, Long childId) {
        if (childId == null || childId <= 0) {
            throw new CustomException(SongErrorCode.INVALID_CHILD_ID);
        }
        Child child = childRepository.findById(childId)
                .orElseThrow(() -> new CustomException(SongErrorCode.CHILD_NOT_FOUND));
        if (!child.getUser().getId().equals(userId)) {
            throw new CustomException(SongErrorCode.CHILD_ACCESS_DENIED);
        }
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
}
