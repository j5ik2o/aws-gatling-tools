# aws-gatling-tools

## How to prepare

```sh
$ brew install awscli jq
```

## How to build

```sh
# gatling-runner docker build & push
$ AWS_DEFAULT_PROFILE=aws-gatling-tools sbt gatling-runner/ecr:push

# gatling-s3-reporter docker build & push
$ cd gatling-s3-reporter
gatling-s3-reporter $ make release

# gatling-aggregate-runner build & push
$ AWS_DEFAULT_PROFILE=aws-gatling-tools sbt gatling-aggregate-runner/ecr:push
```
