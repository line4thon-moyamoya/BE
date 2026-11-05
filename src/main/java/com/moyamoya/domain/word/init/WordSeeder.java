package com.moyamoya.domain.word.init;

import com.moyamoya.domain.question.config.QuestionDataLoader;
import com.moyamoya.domain.question.model.QuestionWordData;
import com.moyamoya.domain.word.entity.Word;
import com.moyamoya.domain.word.repository.WordRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@Profile("seed") // seed 프로필로 실행할 때만 동작
@RequiredArgsConstructor
public class WordSeeder implements ApplicationRunner {

    private final WordRepository wordRepository;
    private final QuestionDataLoader questionDataLoader;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {

        int inserted = 0;
        int skipped = 0;

        // 기존 F2 CSV 로더 재사용
        for (QuestionWordData data : questionDataLoader.getWords()) {

            String word = data.word();

            // 이미 등록된 단어라면 중복 저장 방지
            if (wordRepository.existsByWord(word)) {
                skipped++;
                continue;
            }

            // CSV 데이터 -> Word 엔티티
            Word newWord = Word.createCurated(
                    word,
                    data.definition(),
                    data.category(),
                    data.descAge3To4(),
                    data.descAge5(),
                    data.example(),
                    data.hint(),
                    data.songLine()
            );

            wordRepository.save(newWord);
            inserted++;
        }

        log.info("===== 기본 단어 시딩 완료 =====");
        log.info("신규 저장: {}개", inserted);
        log.info("기존 단어 건너뜀: {}개", skipped);
        log.info("============================");
    }
}
