# for dev purposes only
FROM kestra/kestra:latest
RUN curl -SL https://github.com/docker/compose/releases/download/v2.34.0/docker-compose-linux-x86_64 -o /usr/local/bin/docker-compose && \
    chmod +x /usr/local/bin/docker-compose && \
    docker-compose version

COPY build/libs/* /app/plugins/