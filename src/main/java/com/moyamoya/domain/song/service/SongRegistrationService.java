package com.moyamoya.domain.song.service;

import com.moyamoya.domain.child.entity.Child;
import com.moyamoya.domain.song.entity.Song;
import com.moyamoya.domain.song.entity.SongWord;
import com.moyamoya.domain.song.model.SongRegistrationResult;
import com.moyamoya.domain.song.repository.SongRepository;
import com.moyamoya.domain.song.repository.SongWordRepository;
import com.moyamoya.domain.word.repository.LearnedWordRepository;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SongRegistrationService {
    private static final int CARDS_PER_SONG = 5;
    private final LearnedWordRepository learnedWordRepository;
    private final SongRepository songRepository;
    private final SongWordRepository songWordRepository;

    // 아이 행 잠금을 획득한 카드 저장 트랜잭션에서 호출한다.
    @Transactional(propagation = Propagation.MANDATORY)
    public SongRegistrationResult register(Child child) {
        long couponCount = learnedWordRepository.countUnusedForSongByChildId(child.getId());
        if (couponCount < CARDS_PER_SONG) {
            return new SongRegistrationResult(couponCount, null);
        }
        var cards = learnedWordRepository.findUnusedForSongByChildId(
                child.getId(), PageRequest.of(0, CARDS_PER_SONG));
        if (cards.size() != CARDS_PER_SONG) {
            throw new IllegalStateException("노래에 사용할 카드 5장이 필요합니다.");
        }
        long songNumber = songRepository.countByChild_Id(child.getId()) + 1;
        Song song = songRepository.save(Song.create(child, "우리 노래 " + songNumber));
        var songWords = new ArrayList<SongWord>(CARDS_PER_SONG);
        for (int index = 0; index < CARDS_PER_SONG; index++) {
            songWords.add(SongWord.create(song, cards.get(index), index + 1));
        }
        songWordRepository.saveAll(songWords);
        return new SongRegistrationResult(couponCount - CARDS_PER_SONG, song.getId());
    }
}
