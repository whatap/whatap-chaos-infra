FROM ubuntu:22.04

ARG DEBIAN_FRONTEND=noninteractive
ARG TARGETARCH

# Minimal toolchain for the bash generator + whatap-infra install.
# `ca-certificates` is required so wget can validate repo.whatap.io's TLS
# chain (CloudFront → AmazonS3); without it the deb fetch fails with
# wget exit 5 (SSL verification failure) on minimal Ubuntu builds.
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        bash \
        coreutils \
        procps \
        curl \
        wget \
        ca-certificates \
        uuid-runtime \
    && update-ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Install WhaTap Infra Agent (multi-arch). Same version as the log-flood scenario.
RUN ARCH=${TARGETARCH:-amd64} && \
    wget -q "https://repo.whatap.io/debian/unstable/whatap-infra_2.9.13_${ARCH}.deb" \
        -O /tmp/whatap-infra.deb && \
    dpkg -i /tmp/whatap-infra.deb && \
    rm -f /tmp/whatap-infra.deb

# whatap.conf is mounted at runtime via docker-compose

# Logsink extension config — tails the simulator's log file. The infra
# agent has no APM/transaction context, so every line shipped through
# this path arrives at the gateway with no @txid. That's the whole
# point of this scenario.
COPY extension/logsink.conf /usr/whatap/infra/extension/logsink.conf

# Generator script writes synthetic ERROR-level lines into /var/log/no-txid/
COPY app/solo_logs.sh /opt/solo_logs.sh
RUN chmod +x /opt/solo_logs.sh

RUN mkdir -p /var/log/no-txid

COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

WORKDIR /opt
ENTRYPOINT ["/entrypoint.sh"]
