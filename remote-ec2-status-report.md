# Remote EC2 Chaos Environment — Status Report

**Generated:** 2026-04-26 (KST)
**Discovered via:** `aws ec2 describe-instances` in `ap-northeast-2`
**Caller:** `arn:aws:iam::342996267606:user/hsnam2`

---

## TL;DR

| Question | Answer |
|---|---|
| Where is the chaos host? | EC2 `aiops-chaos-verify-v3` — `i-0df013df81d8a4eed` |
| Public IP | `13.209.75.243` (ssh `ubuntu@13.209.75.243`) |
| Private IP | `172.31.36.202` |
| PEM key to use | `~/tools/pem/DEV-Region.pem` (KeyName=`DEV-Region`) |
| AZ / VPC | `ap-northeast-2c` / `vpc-05e4a2965d81a7a19` |
| SSH from this dev box? | ✅ Verified working |
| `whatap-chaos-infra` cloned on host? | ❌ Not yet — needs git clone |
| `whatap.conf` available on host? | ✅ `/home/whatap/app/chaos/whatap_infra/whatap.conf` |
| Docker installed? | ✅ Docker 29.4.1 |
| Running containers? | Only `mysql-chaos` (mysql:8.0); ap1–ap10 services are not containerized |

---

## 1. AWS CLI Access — verified

```
aws-cli/2.32.30
Account: 342996267606
User:    arn:aws:iam::342996267606:user/hsnam2
Region:  ap-northeast-2 (default in ~/.aws/config)
```

Has `ec2:Describe*` permission on all live instances in this account.

## 2. EC2 inventory (running)

Only **three** running instances in this account (Seoul region). None labeled `chaos`, but cross-referencing private IP against incidents in the local AIOps UI identifies the chaos host unambiguously.

| InstanceId | Name | Type | Private IP | Public IP | AZ | KeyName | Role |
|---|---|---|---|---|---|---|---|
| `i-0130cc0a63dede1f5` | `hsnam-20260317-ubuntu2404` | t3.xlarge | **172.31.61.95** | 3.34.239.54 | 2d | `hsnam_local` | **WhaTap server** (`whatap.server.host` in `whatap.conf`) |
| `i-0724717db14e11bf6` | `whatap-mcp-runner` | t3.small | 172.31.35.74 | 43.201.21.92 | 2c | `ai-cell-hsnam` | MCP runner (whatap-open-mcp project) |
| `i-0df013df81d8a4eed` | `aiops-chaos-verify-v3` | t3.large | **172.31.36.202** | **13.209.75.243** | 2c | **`DEV-Region`** | **Chaos host** ← target for deployment |

**Stopped/stopping instances:** none.

### Why `aiops-chaos-verify-v3` is the chaos host

- Its private IP `172.31.36.202` matches the host that emits chaos data the local AIOps surfaces (e.g. *"order_service가 배포된 호스트(ip-172-31-36-202) 의 전체 메모리 사용률이 93%"* incident).
- Same VPC + adjacent subnet to the WhaTap server, so its WhaTap-Infra agent can ship to `172.31.61.95` without crossing VPC peering.
- Tag `Name = aiops-chaos-verify-v3` aligns with the chaos-validation purpose.

## 3. Connectivity probe

```bash
ssh -i ~/tools/pem/DEV-Region.pem ubuntu@13.209.75.243
# →  hostname: ip-172-31-36-202
# →  arch:     x86_64
# →  uptime:   1 day 13:38, load 0.12 0.05 0.01
```

**Note on the security group:** SG `sg-07651c161b0171d9e` (name `aiops-chaos-verify-1777021899`) shows ingress only from `15.165.167.245/32` for ports 22 / 9090 / 8081-8090, yet SSH from this box (egress `175.113.236.204`) succeeds. Possible reasons: NAT/VPN egress mapping, or there are additional rules not surfaced by the first query. **Not an issue right now**, but if SSH starts failing later, add the active IP via `aws ec2 authorize-security-group-ingress --group-id sg-07651c161b0171d9e --protocol tcp --port 22 --cidr <my-ip>/32`.

## 4. State on the chaos host

```
~ docker --version    → Docker version 29.4.1
~ docker ps           → mysql-chaos (mysql:8.0) only
~ ls /home/whatap/app/chaos/    → exists (mirrors the dev tree at /home/whatap/app/chaos here)
~ find /home -name whatap.conf  → /home/whatap/app/chaos/whatap_infra/whatap.conf
                                  /home/whatap/app/chaos/whatap/whatap.conf
~ ls /home/ubuntu/whatap-chaos-infra        → MISSING
~ ls /home/whatap/app/chaos/whatap-chaos-infra → MISSING
```

Implications:

- The chaos host has Docker but no `whatap-chaos-infra` repo yet; the existing chaos infra (ap1–ap10 microservices) runs **outside** Docker (likely systemd) since only `mysql-chaos` is container-resident.
- The `whatap.conf` already on the host (license + `whatap.server.host=172.31.61.95` + `pcode=33`) is the same one the user pasted earlier. It can be reused directly.

## 5. AIOps Swarm cluster (separate account)

The AIOps stack itself runs on a Docker Swarm cluster of two EC2 hosts. Per the user, these are the actual AIOps consumers (Anomaly-Detection / Dashboard / OpsLake / Milvus / MySQL / Redis / MinIO live here):

| Role | Private IP | SSH |
|---|---|---|
| **Swarm leader** (`self-opslake-01`) | `172.31.42.221` | `ssh -i ~/tools/pem/DEV-Region.pem ubuntu@172.31.42.221` |
| **Swarm worker** (`self-opslake-02`) | `172.31.35.123` | `ssh -i ~/tools/pem/DEV-Region.pem ubuntu@172.31.35.123` |

Same PEM key (`DEV-Region.pem`) as the chaos host, so credentials are interchangeable.

**Discovery gap:** these instances do **not** appear in this AWS account (`342996267606`) under any of the queried regions: `ap-northeast-2`, `us-east-1`, `us-west-2`, `ap-northeast-1`, `eu-west-1`. They live in a **different AWS account**. AWS-CLI work targeting the swarm cluster will need a different profile (or rely on direct SSH, which works from this dev box).

**End-to-end data flow** for the no-txid scenario, after deploy:

```
[chaos host 172.31.36.202]                          [swarm leader/worker — different account]
chaos-no-txid-logs container                        anomaly-detection (DETECTION_MODE=batch
  whatap_infrad → tails /var/log/no-txid/*.log         pinned to opslake-02)
       │                                              ↑
       │ WhaTap log-server protocol                  │
       ▼                                              │
[WhaTap server 172.31.61.95]                         │
   pcode=33 ingestion                                │
       │                                              │
       │ → OpsLake hot/logs/AppLog parquet writes    │
       ▼                                              │
[some MinIO/object store reachable by both]──────────┘
                                          batch worker LogProcessor
                                          → ErrorQueue (no-txid → solo path)
                                          → solo_admitted_log counter / RCA
```

The swarm cluster is the consumer side; the chaos host is the producer side. After the deploy on the chaos host (§6), the validation work happens on the swarm cluster at the AIOps UI (`http://172.31.39.76:8080/aiops/anomalies/`).

## 6. Recommended next steps for the no-txid log scenario deploy

1. **SSH to the chaos host:**
   ```bash
   ssh -i ~/tools/pem/DEV-Region.pem ubuntu@13.209.75.243
   ```

2. **Clone whatap-chaos-infra into a sensible location:**
   ```bash
   sudo mkdir -p /home/whatap/app/chaos/whatap-chaos-infra
   sudo chown -R ubuntu /home/whatap/app/chaos/whatap-chaos-infra
   git clone https://github.com/whatap/whatap-chaos-infra.git \
     /home/whatap/app/chaos/whatap-chaos-infra
   ```
   (The repo HEAD on `main` is commit `c4f8e92` — contains the new `runner/no-txid-logs/` scenario.)

3. **Wire up `whatap.conf`:**
   ```bash
   cd /home/whatap/app/chaos/whatap-chaos-infra
   cp /home/whatap/app/chaos/whatap_infra/whatap.conf ./whatap.conf
   ```
   The existing file already has license + `whatap.server.host=172.31.61.95` + `pcode=33` — no edits needed.

4. **Bring up only the no-txid scenario** (independent of `runner` and `log-flood`):
   ```bash
   docker compose up -d no-txid-logs
   docker logs -f chaos-no-txid-logs   # confirm whatap_infrad starts
   ```

5. **Verify on the local AIOps cluster** (already monitoring `pr33` per Settings UI):
   - `/api/status` → `counters.solo_admitted_log` should tick up after a few minutes
   - `docker logs aiops-anomaly-detection 2>&1 | grep "ErrorQueue.*solo log admitted"` → rate-limited INFO lines
   - `mysql -e "SELECT id, oname, content_preview FROM rca_document WHERE category='anomaly_history' AND oname='chaos-no-txid-logs' ORDER BY id DESC LIMIT 5"` → expect generated RCAs

## 7. Open risks / caveats

- **Memory budget on chaos host:** t3.large has ~7.6 GB RAM and is already hosting `mysql-chaos` plus 10 Spring Boot microservices (ap1–ap10). The new `no-txid-logs` container has been capped at `256M` so impact should be small, but worth eyeballing `free -h` after start.
- **Logsink path collision:** the new container tails `/var/log/no-txid/*.log` *inside its own container*; nothing on the chaos host's filesystem is written, so no risk of clashing with the existing chaos services' logs.
- **WhaTap-Infra agent version:** the Dockerfile pins `whatap-infra_2.9.13_${ARCH}.deb` (matching the existing log-flood scenario). If the WhaTap server at `172.31.61.95` requires a newer protocol, the version pin can be bumped in `runner/no-txid-logs/Dockerfile` line 18.
