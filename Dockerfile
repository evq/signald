ARG BUILDER_IMAGE=gradle:jdk8
ARG RUNNER_IMAGE=openjdk:8-jre-slim

FROM $BUILDER_IMAGE as builder
USER root

COPY . /tmp/src
WORKDIR /tmp/src

RUN gradle build --no-daemon
RUN tar xf build/distributions/signald.tar -C /opt

FROM $RUNNER_IMAGE

RUN mkdir /app

COPY --from=builder /opt/signald /opt/signald

RUN ln -sf /opt/signald/bin/signald /usr/local/bin/

RUN addgroup -g 1001 -S signald && adduser -u 1001 -S signald -G signald
# basically `make setup`
RUN mkdir -p /var/run/signald
RUN chown signald /var/run/signald

USER signald
VOLUME /home/signald/.config/signald
VOLUME /var/run/signald
ENTRYPOINT ["/usr/local/bin/signald"]
