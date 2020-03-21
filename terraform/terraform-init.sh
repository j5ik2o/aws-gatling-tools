#!/usr/bin/env bash

set -eu

cd $(dirname $0)

terraform init -backend=false
# \
#  -backend=true \
#  -backend-config="bucket=${TF_BUCKET_NAME}" \
#  -backend-config="key=${TF_STATE_NAME}" \
#  -backend-config="region=${AWS_REGION}" \
#  -backend-config="profile=${AWS_PROFILE}" \
#  -backend-config="encrypt=true" \
#  --var-file=terraform.tfvars
