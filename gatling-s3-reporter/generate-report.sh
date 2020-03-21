#!/bin/sh

set -eu

if [[ -z ${TW_GATLING_BUCKET_NAME} ]]; then
  echo "env TW_GATLING_BUCKET_NAME does not exist"
  exit 1
fi

if [[ -z ${TW_GATLING_RESULT_DIR_PATH} ]]; then
  echo "env TW_GATLING_RESULT_DIR_PATH does not exist"
  exit 1
fi

mkdir -p ${TW_GATLING_RESULT_DIR_PATH}

# copy logs from s3
/usr/bin/aws s3 cp s3://${TW_GATLING_BUCKET_NAME}/${TW_GATLING_RESULT_DIR_PATH}/ ${GATLING_HOME}/results/${TW_GATLING_RESULT_DIR_PATH} --recursive --exclude "*" --include "*.log"

# create report
/opt/gatling/bin/gatling.sh -ro ${TW_GATLING_RESULT_DIR_PATH}

# copy report files to s3 (excluding logs)
/usr/bin/aws s3 cp ${GATLING_HOME}/results/${TW_GATLING_RESULT_DIR_PATH} s3://${TW_GATLING_BUCKET_NAME}/${TW_GATLING_RESULT_DIR_PATH}/ --recursive --exclude "*.log"

echo [report url] https://${TW_GATLING_BUCKET_NAME}.s3.amazonaws.com/${TW_GATLING_RESULT_DIR_PATH}/index.html
