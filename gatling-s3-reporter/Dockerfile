FROM openjdk:8-jdk-alpine

LABEL version=0.0.1

ARG AWS_VERSION=1.16.58
ARG GATLING_VERSION=3.1.2


RUN apk --no-cache add py3-pip jq \
    && pip3 install --no-cache-dir --upgrade pip \
    && pip3 install --no-cache-dir awscli==${AWS_VERSION}

WORKDIR /opt

# install gatling
RUN mkdir -p gatling && mkdir -p work && apk add --update wget bash libc6-compat

RUN mkdir -p /tmp/downloads && \
  wget -q -O /tmp/downloads/gatling-${GATLING_VERSION}.zip https://repo1.maven.org/maven2/io/gatling/highcharts/gatling-charts-highcharts-bundle/${GATLING_VERSION}/gatling-charts-highcharts-bundle-${GATLING_VERSION}-bundle.zip

RUN mkdir -p /tmp/archive && cd /tmp/archive && \
  unzip /tmp/downloads/gatling-${GATLING_VERSION}.zip && \
  mv /tmp/archive/gatling-charts-highcharts-bundle-${GATLING_VERSION}/* /opt/gatling/ && \
  rm -rf /tmp/*

# change context to gatling directory
WORKDIR  /opt/work

ENV GATLING_HOME /opt/gatling

COPY generate-report.sh /generate-report.sh

ENTRYPOINT ["/generate-report.sh"]
