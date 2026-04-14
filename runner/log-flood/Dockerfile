FROM ubuntu:22.04

ARG DEBIAN_FRONTEND=noninteractive
ARG TARGETARCH

# Install OpenJDK 17 and basic utilities
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        openjdk-17-jdk-headless \
        procps \
        curl \
        iproute2 \
        wget \
    && rm -rf /var/lib/apt/lists/*

# Download and install WhaTap Infra Agent (multi-arch)
RUN ARCH=${TARGETARCH:-amd64} && \
    wget -q "https://repo.whatap.io/debian/unstable/whatap-infra_2.9.13_${ARCH}.deb" \
        -O /tmp/whatap-infra.deb && \
    dpkg -i /tmp/whatap-infra.deb && \
    rm -f /tmp/whatap-infra.deb

# whatap.conf is mounted at runtime via docker-compose

# Copy WhaTap Infra logsink extension config
COPY extension/logsink.conf /usr/whatap/infra/extension/logsink.conf

# Compile the Java application
COPY app/LogFloodBatch.java /opt/batch/LogFloodBatch.java
RUN javac /opt/batch/LogFloodBatch.java -d /opt/batch/

# Create log directory
RUN mkdir -p /var/log/batch

# Copy entrypoint
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

WORKDIR /opt/batch

ENTRYPOINT ["/entrypoint.sh"]
