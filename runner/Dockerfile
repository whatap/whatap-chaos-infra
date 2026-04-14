FROM eclipse-temurin:17-jdk

ARG TARGETARCH

RUN apt-get update && \
    apt-get install -y --no-install-recommends curl procps docker.io wget && \
    rm -rf /var/lib/apt/lists/*

# Download and install WhaTap Infra Agent (also runs on the runner itself)
RUN mkdir -p /app && \
    ARCH=${TARGETARCH:-amd64} && \
    wget -q "https://repo.whatap.io/debian/unstable/whatap-infra_2.9.13_${ARCH}.deb" \
        -O /tmp/whatap-infra.deb && \
    dpkg -i /tmp/whatap-infra.deb && \
    rm -f /tmp/whatap-infra.deb

# Copy and compile scenario runner
COPY src /app/src
COPY web /app/web

WORKDIR /app
RUN mkdir -p out && \
    find src -name "*.java" > /tmp/sources.txt && \
    javac -d out @/tmp/sources.txt && \
    cp -r web out/web && \
    rm -f /tmp/sources.txt

# Copy scenario definitions
COPY trouble /app/trouble

# Copy entrypoint
COPY entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

EXPOSE 9090

ENTRYPOINT ["/app/entrypoint.sh"]
