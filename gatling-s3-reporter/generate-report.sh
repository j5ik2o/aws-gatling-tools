#!/bin/sh

set -eu

if [[ -z ${GATLING_BUCKET_NAME} ]]; then
  echo "env GATLING_BUCKET_NAME does not exist"
  exit 1
fi

if [[ -z ${GATLING_RESULT_DIR_PATH} ]]; then
  echo "env GATLING_RESULT_DIR_PATH does not exist"
  exit 1
fi

mkdir -p ${GATLING_RESULT_DIR_PATH}

# copy logs from s3
/usr/bin/aws s3 cp s3://${GATLING_BUCKET_NAME}/${GATLING_RESULT_DIR_PATH}/ ${GATLING_HOME}/results/${GATLING_RESULT_DIR_PATH} --recursive --exclude "*" --include "*.log"

# create report
/opt/gatling/bin/gatling.sh -ro ${GATLING_RESULT_DIR_PATH}

# copy report files to s3 (excluding logs)
/usr/bin/aws s3 cp ${GATLING_HOME}/results/${GATLING_RESULT_DIR_PATH} s3://${GATLING_BUCKET_NAME}/${GATLING_RESULT_DIR_PATH}/ --recursive --exclude "*.log"

echo [report url] https://${GATLING_BUCKET_NAME}.s3.amazonaws.com/${GATLING_RESULT_DIR_PATH}/index.html
