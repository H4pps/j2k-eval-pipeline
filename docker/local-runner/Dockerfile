FROM eclipse-temurin:21-jdk-noble

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        bash \
        ca-certificates \
        curl \
        fontconfig \
        fonts-dejavu-core \
        git \
        gzip \
        libfreetype6 \
        libxi6 \
        libxrandr2 \
        libxrender1 \
        libxtst6 \
        procps \
        tar \
        unzip \
        xvfb \
        xz-utils \
        zip \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace
