# 모야, 모야? 👽 — Backend

> 아이가 궁금해한 말을 모야가 찾아주고, 모은 단어로 노래를 만들어요.

<br />

## 📖 About

**모야, 모야?** 

아이가 목소리로 질문하면 발음과 문맥을 바탕으로 단어를 찾고, 연령에 맞는 설명을 제공합니다. 학습한 단어는 수첩과 개인화 노래로 이어지고, 보호자는 아이의 질문과 학습 기록을 확인할 수 있어요.

### 🔧 How we built

| 구현 | 설명 |
| --- | --- |
| **🎙️ 음성 질문** | CLOVA Speech로 음성을 인식하고 질문에서 궁금한 단어를 추출 |
| **🔎 단어 탐색** | 한국어 자모 유사도와 아동 발음 변형 규칙으로 후보를 찾고, 되묻기에서 추가 문맥을 반영 |
| **📖 맞춤 설명** | 표준국어대사전과 OpenAI를 활용해 외부 단어를 탐색하고 연령별 쉬운 설명 생성 |
| **🎵 단어 노래** | 사용 가능한 학습 카드 5개를 모아 개인화 노래를 비동기로 생성 |
| **👨‍👩‍👧 보호자 모드** | PIN 인증, 다자녀 프로필, 미해결 질문 확인과 주간 리포트 제공 |
| **🔊 캐릭터 음성** | Google Cloud TTS와 Python 음성 후처리로 모야 음성 구현 |
| **☁️ 파일 관리** | 이미지·음성을 S3에 저장하고 Presigned URL로 제공 |
| **🚀 배포** | GitHub Actions로 빌드·테스트 후 EC2에 Docker Compose로 배포 |

<br />

## 🛠 Tech Stack

| 분류 | 기술 |
| --- | --- |
| Language | Java 21, Python 3.11 |
| Framework | Spring Boot 3.4.5, RestAPI |
| Database | MySQL 8.4, Spring Data JPA |
| Security | Spring Security, JWT |
| AI · Speech | CLOVA Speech, Google Cloud TTS, OpenAI API |
| Audio Processing | Parselmouth · Praat, FFmpeg |
| Storage | AWS S3 |
| Deploy | AWS EC2, Nginx, Docker Compose, GitHub Actions |
| API Docs | Swagger UI |

<br />

## 📁 Folder Structure

```text
BE
├── src/main/java/com/moyamoya
│   ├── domain          # 인증, 아이, 질문, 단어, 노래, 리포트 등
│   ├── global          # 공통 응답, 예외, 설정, S3
│   └── security        # JWT 인증과 보호자 모드
├── src/main/resources  # 환경 설정, 단어 데이터, 프롬프트
├── src/test            # 백엔드 테스트
├── tts-processor       # Python 음성 후처리 서버
├── deploy              # 배포 스크립트와 DB 변경 SQL
└── .github/workflows   # 빌드·테스트·자동배포
```

<br />

## 🚀 Getting Started

JDK 21, 로컬 MySQL과 음성 처리 서버를 준비해 주세요.

레포지토리 루트의 `.env`에 DB·JWT·CLOVA·사전·OpenAI 설정을 넣고, Google Cloud와 AWS 인증도 준비해야 해요. 필요한 변수는 `application.yml`과 `application-dev.yml`에서 확인할 수 있습니다.

```bash
# 1. 레포지토리 클론
git clone https://github.com/line4thon-moyamoya/BE.git
cd BE

# 2. Python 음성 처리 서버 실행
docker build -t moyamoya-tts-dev ./tts-processor
docker run -d --name moyamoya-tts-dev \
  -p 127.0.0.1:8001:8001 moyamoya-tts-dev

# 3. 환경 설정 후 개발 서버 실행
./gradlew bootRun --args='--spring.profiles.active=dev'
```

| 명령어 | 설명 |
| --- | --- |
| `./gradlew bootRun` | 개발 서버 실행 |
| `./gradlew test` | Java 테스트 |
| `./gradlew clean build` | 테스트와 빌드 |

<br />

## 🔀 Workflow

```text
이슈 생성 → develop에서 작업 브랜치 생성 → 작업 & 커밋 → PR → 리뷰 · CI → merge
```

- 작업 브랜치는 `feat/작업명`, `fix/작업명` 등의 형식을 사용해요.
- 작업 PR은 `develop`으로 올리고, 리뷰 반영과 CI 확인 후 Squash merge해요.
- 운영 배포는 `develop`에서 `main`으로 PR을 생성해요.
- 커밋은 `✨ Feat: 음성 질문 기능 추가`처럼 작성해요.

> `main`에 반영되면 GitHub Actions가 운영 배포를 실행해요. DB 변경 SQL은 운영 DB 상태를 확인하고 배포 시점에 맞춰 별도로 적용합니다.

<br />

## 📚 API Docs

[Swagger UI 바로가기](https://api.timeto.asia/swagger-ui/index.html)

<br />

## 👥 Team

<table>
  <tr>
    <td align="center">
      <img src="https://avatars.githubusercontent.com/u/164372848?s=160" width="160" alt="riveryunny" />
    </td>
    <td align="center">
      <img src="https://avatars.githubusercontent.com/u/155218786?s=160" width="160" alt="hasol11" />
    </td>
    <td align="center">
      <img src="https://avatars.githubusercontent.com/u/168630116?s=160" width="160" alt="yeyun0423" />
    </td>
  </tr>
  <tr>
    <td align="center"><a href="https://github.com/riveryunny"><b>@riveryunny</b></a></td>
    <td align="center"><a href="https://github.com/hasol11"><b>@hasol11</b></a></td>
    <td align="center"><a href="https://github.com/yeyun0423"><b>@yeyun0423</b></a></td>
  </tr>
  <tr>
    <td align="center">Backend</td>
    <td align="center">Backend</td>
    <td align="center">Backend</td>
  </tr>
</table>
