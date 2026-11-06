# EC2 demo deployment

## EC2 prerequisites

- Ubuntu 24.04 LTS
- Security group inbound: SSH (22) from the administrator IP and API (8080) from the frontend/testing IPs
- Docker Engine and Docker Compose plugin

Do not expose MySQL port 3306 to the internet. The Compose configuration only exposes the API port.

## First deployment

```bash
git clone https://github.com/line4thon-moyamoya/BE.git
cd BE
cp .env.example .env
nano .env
docker compose up -d --build
docker compose ps
docker compose logs -f app
```

Replace all `change-me` values in `.env`. Generate the JWT secret with:

```bash
openssl rand -base64 48
```

## Verify

```bash
curl http://localhost:8080/api/v1/consents
```

## Update

```bash
git pull --ff-only
docker compose up -d --build
docker image prune -f
```

## Backup

```bash
docker compose exec -T db mysqldump -u root -p"$MYSQL_ROOT_PASSWORD" moyamoya > moyamoya-backup.sql
```

The initial consent text is demo placeholder copy from the API specification. Replace it with approved legal text before a real release.
