#!/bin/sh

export AWS_DEFAULT_PROFILE=aws-gatling-tools

sbt api-server/ecr:push gatling-runner/ecr:push gatling-aggregate-runner/ecr:push
cd gatling-s3-reporter && make release && cd ..
