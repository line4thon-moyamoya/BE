# 배포 메모

EC2에서 저장소 루트를 기준으로 실행한다. Docker Compose와 서버용 `.env`, Google 인증 파일을 먼저 준비한다.

## 최초 실행

```bash
docker compose config --quiet
docker compose build
docker compose up -d db tts-processor
SPRING_PROFILES_ACTIVE=prod,seed docker compose up -d app
docker compose logs -f app
```

기본 단어 시딩 완료 로그를 확인한 뒤 `Ctrl+C`로 로그 조회를 종료하고 일반 프로필로 전환한다.

```bash
docker compose up -d app
docker compose ps
curl --fail http://localhost:8080/api/v1/consents
```

`.env`에 `SPRING_PROFILES_ACTIVE`를 지정하지 않으면 기본값은 `prod`다. 단어 시딩은 누락된 단어만 추가하며 TTS를 미리 생성하지는 않는다.

## 동의 문구 변경

`mysql-init.sql`은 데이터 볼륨이 비어 있을 때만 실행된다. 기존 DB의 문구는 별도 SQL로 갱신한다. 현재 문구는 데모용이며 실제 이용약관으로 사용할 수 없다.

먼저 백업한다.

```bash
docker compose exec -T db sh -c 'exec mysqldump -u root -p"$MYSQL_ROOT_PASSWORD" moyamoya' > "moyamoya-$(date +%Y%m%d-%H%M%S).sql"
```

명령이 성공하고 백업 파일이 비어 있지 않은지 확인한 뒤 적용한다.

```bash
docker compose exec -T db sh -c 'exec mysql --default-character-set=utf8mb4 -u root -p"$MYSQL_ROOT_PASSWORD" moyamoya' < deploy/migrations/001_update_consents.sql
curl --fail http://localhost:8080/api/v1/consents
```

SQL이 실패하면 배포를 멈추고 원인을 확인한다. 백업 파일은 저장소 밖에 보관한다.

`consent_type` 기준으로 갱신하므로 기존 ID와 동의 기록은 유지된다. 같은 SQL을 다시 실행해도 중복 행은 생기지 않는다. 이후 문구 변경 시 초기화 SQL을 수정하고 새 번호의 마이그레이션을 추가한다. 적용한 파일은 수정하지 않고 배포 기록에 파일명을 남긴다. 동의 버전 변경이나 재동의 처리는 별도 작업이다.

## 인증 설정

- CLOVA 호출 URL과 Secret Key는 서버 `.env`에 설정한다.
- Google 인증 JSON은 `/opt/moyamoya/secrets/google-tts.json`에 두고 컨테이너 사용자에게 읽기 권한을 준다. 다른 경로를 쓰면 `.env`의 `GOOGLE_TTS_CREDENTIALS_FILE`을 변경한다.
- S3는 EC2 IAM Role을 사용한다. 버킷 소유자에게 Role ARN을 전달하고 양쪽 계정에서 `arn:aws:s3:::moyamoya-audio-dev-moya/prod/*`의 `s3:GetObject`, `s3:PutObject`를 허용한다. KMS 암호화 버킷이면 키 권한도 확인한다.
- Docker 내부에서 EC2 Role을 사용하려면 IMDSv2를 활성화하고 metadata response hop limit을 2로 설정한다.
- MySQL과 Python 포트는 외부에 열지 않는다. 프론트 주소는 `CORS_ALLOWED_ORIGINS`에 설정한다.

설정 파일에 넣을 운영 토큰 만료시간은 Access 900000, Refresh 2592000000, Parent Mode 600000(ms)이다. DB 비밀번호는 `.env`의 `MYSQL_PASSWORD`를 Spring에도 전달한다.

API가 켜진 뒤에는 로그인, 음성 질문, 후보 확정까지 확인한다. 동의 목록 조회만으로 외부 서비스 연결을 확인할 수는 없다.
