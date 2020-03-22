# aws-gatling-tools

STATUS: WIP

## How to prepare

- installing tool
    ```sh
    $ brew install sbt awscli jq
    ```
- register an IAM user in AWS account
- add a profile for the IAM user as `aws-gatling-tools` to `~/.aws/credentails`.
    ```sh
    [aws-gatling-tools]
    aws_access_key_id = XXXXX
    aws_secret_access_key = XXXXX
    region = ap-northeast-1 
    ```

## How to build

```sh
# gatling-runner docker build & push
$ AWS_DEFAULT_PROFILE=aws-gatling-tools sbt gatling-runner/ecr:push

# gatling-s3-reporter docker build & push
$ cd gatling-s3-reporter && make release && cd ..

# gatling-aggregate-runner build & push
$ AWS_DEFAULT_PROFILE=aws-gatling-tools sbt gatling-aggregate-runner/ecr:push

# api-server docker build & push
$ AWS_DEFAULT_PROFILE=aws-gatling-tools sbt api-server/ecr:push
```

## How to run a stress-test

```sh
$ AWS_PROFILE=aws-gatling-tools \
    GATLING_NOTICE_SLACK_INCOMING_WEBHOOK_URL=https://hooks.slack.com/services/xxxxx \
    GATLING_TARGET_HOST=XXXXX.XXXXXX.XXXXX \
    sbt gatling-aggregate-runner/gatling::runTask
```
