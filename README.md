# WhaTap Chaos Infra - Log Flood Demo

WhaTap 인프라 모니터링을 위한 카오스 엔지니어링 시나리오.
배치 프로세스 버그로 인한 로그 파일 폭증 상황을 시뮬레이션하여, 디스크/아이노드 사용량 급증을 재현합니다.

## 구성

1. **Scenario Runner** (포트 9090): REST API + 웹 UI로 시나리오 제어
2. **Log Flood Container**: 초당 ~15개 로그 파일(~7.5 MB/sec)을 생성하는 Java 배치 앱
3. **WhaTap Infra Agent**: 두 컨테이너 모두에서 디스크, 아이노드, CPU 모니터링

## 빠른 시작

### 사전 요구사항
- Docker + Docker Compose
- WhaTap 계정 (https://www.whatap.io)

### 설정

```bash
git clone https://github.com/whatap/whatap-chaos-infra.git
cd whatap-chaos-infra

# whatap.conf 설정 (라이선스 키와 서버 IP 입력)
cp whatap.conf.template whatap.conf
vi whatap.conf
```

### 실행

```bash
# 전체 시작 (Runner + Log Flood 컨테이너)
./start_runner.sh

# 웹 UI 접속
open http://localhost:9090
```

### 시나리오 실행

```bash
# 로그 폭증 시작
./trouble/log-flood/start.sh

# 로그 폭증 중지
./trouble/log-flood/stop.sh

# 전체 중지
./stop_runner.sh
```

### No-TXID 로그 시뮬레이터 (AIOps "solo log admitted" 검증용)

WhaTap-Infra 에이전트의 logsink 모듈로 ERROR 레벨 로그를 흘려보내는
독립 컨테이너. APM 컨텍스트가 없으므로 모든 라인이 `@txid` 없이
게이트웨이에 도달 — AIOps `ErrorQueue`의 솔로 로그(no-txid) 처리
경로를 검증할 때 사용.

```bash
# 시작 (runner 와 무관, 단독 기동 가능)
docker compose up -d no-txid-logs

# 로그 확인
docker logs -f chaos-no-txid-logs
docker exec chaos-no-txid-logs tail -f /var/log/no-txid/app.log

# 중지
docker compose stop no-txid-logs
```

생성 주기는 `NO_TXID_INTERVAL_SEC`(기본 5초, ±jitter)로 조절. 6개의
배경 워커 에러 패턴(Quartz, Kafka consumer, Spring boot startup,
JVM thread, HikariCP, AOP)을 무작위로 발생.

## 동작 방식

```
./start_runner.sh
  │
  ├─ chaos-log-flood (정상 모드)
  │    ├─ WhaTap Infra Agent (베이스라인 수집)
  │    └─ 1GB ext4 볼륨 (/var/log/batch) - 디스크 사용률 1%
  │
  └─ chaos-runner (:9090)
       ├─ WhaTap Infra Agent
       └─ REST API + 웹 UI

./trouble/log-flood/start.sh
  │
  └─ chaos-log-flood (장애 모드)
       ├─ WhaTap Infra Agent (장애 감지)
       ├─ Java 배치 프로세스 (로그 파일 폭증)
       └─ 1GB ext4 볼륨 - 약 2분 후 100% 도달

./trouble/log-flood/stop.sh
  │
  └─ chaos-log-flood (정상 모드로 복구)
       ├─ Java 프로세스 종료
       └─ 로그 파일 정리 → 디스크 사용률 1%로 복구
```

## WhaTap 설정

`whatap.conf`에 프로젝트 라이선스와 서버 IP를 설정합니다:

```
license=<프로젝트-라이선스-키>
whatap.server.host=<와탭-수집서버-IP>
```

WhaTap 콘솔에서 확인: **인프라 모니터링 > 프로젝트 > 관리 > 에이전트 설치**

### 컨테이너별 에이전트 이름

`docker-compose.yml`의 `WHATAP_ONAME` 환경변수로 설정됩니다:

| 컨테이너 | oname | 역할 |
|----------|-------|------|
| chaos-runner | `chaos-runner` | 시나리오 러너 |
| chaos-log-flood | `chaos-log-flood` | 배치 서버 (디스크 폭증 대상) |

## API

| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/v1/health` | 서버 상태 |
| GET | `/api/v1/scenarios` | 시나리오 목록 |
| POST | `/api/v1/scenarios/c01_log_flood/start` | 로그 폭증 시작 |
| POST | `/api/v1/scenarios/c01_log_flood/stop` | 로그 폭증 중지 |
| GET | `/api/v1/scenarios/c01_log_flood/status` | 상태 확인 |
| POST | `/api/v1/scenarios/stop-all` | 긴급 중지 |

### 커스텀 파라미터

```bash
curl -X POST http://localhost:9090/api/v1/scenarios/c01_log_flood/start \
  -H "Content-Type: application/json" \
  -d '{"params":{"file_size_kb":"1024","interval_ms":"100","files_per_iter":"5"}}'
```

## 파라미터

| 이름 | 기본값 | 설명 |
|------|--------|------|
| `file_size_kb` | 512 | 로그 파일 크기 (KB) |
| `interval_ms` | 200 | 반복 간격 (ms) |
| `files_per_iter` | 3 | 반복당 생성 파일 수 |
| `DISK_LIMIT_MB` | 1024 | ext4 볼륨 크기 (MB), docker-compose.yml에서 설정 |

## WhaTap 대시보드에서 관찰할 항목

시나리오 시작 후 WhaTap 대시보드에서 다음 지표를 확인하세요:

- **디스크 사용률 (%)**: `/var/log/batch` 파티션 급증
- **아이노드 사용률 (%)**: 파일 수 증가에 따른 아이노드 소모
- **디스크 I/O**: 높은 쓰기 처리량
- **CPU (%)**: Java 배치 프로세스로 인한 증가
- **로그 모니터링**: `batchlog` 카테고리의 로그 폭증

디스크 사용률 80% 초과 알림을 설정하여 모니터링 파이프라인을 테스트하세요.

## 지원 플랫폼

| 플랫폼 | 아키텍처 | 상태 |
|--------|----------|------|
| Linux | amd64 | 지원 |
| macOS (Apple Silicon) | arm64 | 지원 |

## 프로젝트 구조

```
whatap-chaos-infra/
├── start_runner.sh              # 전체 시작
├── stop_runner.sh               # 전체 중지
├── docker-compose.yml           # Runner + Log Flood 컨테이너 정의
├── whatap.conf                  # WhaTap 설정 (사용자 생성)
├── whatap.conf.template         # WhaTap 설정 템플릿
├── trouble/
│   └── log-flood/
│       ├── start.sh             # 로그 폭증 시작 (API 호출)
│       └── stop.sh              # 로그 폭증 중지 (API 호출)
└── runner/
    ├── Dockerfile               # 시나리오 러너 이미지
    ├── entrypoint.sh            # WhaTap 에이전트 + Java 서버 시작
    ├── src/                     # Java REST API 소스 (7개 파일)
    ├── web/index.html           # 웹 UI (다크 테마 대시보드)
    ├── trouble/c01_log_flood/   # 시나리오 스크립트 (컨테이너 내부용)
    └── log-flood/               # Log Flood 컨테이너 소스
        ├── Dockerfile           # Ubuntu + JDK + WhaTap Infra Agent
        ├── app/LogFloodBatch.java
        ├── entrypoint.sh        # ext4 볼륨 생성 + WhaTap 에이전트 시작
        └── extension/logsink.conf
```
