FROM alpine:3.22

ARG ACT_VERSION=0.2.88
ARG TARGETARCH

RUN apk add --no-cache \
        bash \
        ca-certificates \
        docker-cli \
        git \
        gzip \
        tar \
        wget \
    && case "${TARGETARCH}" in \
        amd64) act_arch="x86_64" ;; \
        arm64) act_arch="arm64" ;; \
        arm) act_arch="armv7" ;; \
        *) echo "Unsupported TARGETARCH=${TARGETARCH}" >&2; exit 1 ;; \
    esac \
    && wget -q -O /tmp/act.tar.gz "https://github.com/nektos/act/releases/download/v${ACT_VERSION}/act_Linux_${act_arch}.tar.gz" \
    && tar -C /usr/local/bin -xzf /tmp/act.tar.gz act \
    && chmod +x /usr/local/bin/act \
    && rm /tmp/act.tar.gz

ENTRYPOINT ["act"]
