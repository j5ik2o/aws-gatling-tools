import com.amazonaws.regions.{Region, Regions}
import com.github.j5ik2o.reactive.aws.ecs.EcsAsyncClient
import com.github.j5ik2o.reactive.aws.ecs.implicits._
import com.typesafe.sbt.SbtNativePackager.autoImport.{maintainer, packageName}
import com.typesafe.sbt.packager.archetypes.scripts.BashStartScriptPlugin.autoImport.bashScriptExtraDefines
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.{
  dockerBaseImage,
  dockerUpdateLatest,
  _
}
import sbt.Keys._
import sbt.internal.util.ManagedLogger
import sbt.{CrossVersion, Resolver, settingKey, taskKey, _}
import sbtecr.EcrPlugin.autoImport.{
  localDockerImage,
  login,
  push,
  region,
  repositoryName,
  repositoryTags,
  _
}
import software.amazon.awssdk.services.ecs.model.{AssignPublicIp, Task, _}
import software.amazon.awssdk.services.ecs.{
  EcsAsyncClient => JavaEcsAsyncClient
}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

object Settings {
  val gatlingVersion = "3.1.0"
  val circeVersion = "0.11.1"
  val awsSdkVersion = "1.11.575"
  val akka26Version = "2.6.4"

  val baseSettings =
    Seq(
      version := "1.0.0",
      scalaVersion := "2.13.1",
      scalacOptions ++= {
        Seq(
          "-feature",
          "-deprecation",
          "-unchecked",
          "-encoding",
          "UTF-8",
          "-language:_",
          "-target:jvm-1.8"
        ) ++ {
          CrossVersion.partialVersion(scalaVersion.value) match {
            case Some((2L, scalaMajor)) if scalaMajor >= 12 =>
              Seq.empty
            case Some((2L, scalaMajor)) if scalaMajor <= 11 =>
              Seq("-Yinline-warnings")
          }
        }
      },
      resolvers ++= Seq(
        "Sonatype OSS Snapshot Repository" at "https://oss.sonatype.org/content/repositories/snapshots/",
        "Sonatype OSS Release Repository" at "https://oss.sonatype.org/content/repositories/releases/",
        "DynamoDB Local Repository" at "https://s3-ap-northeast-1.amazonaws.com/dynamodb-local-tokyo/release",
        "Seasar Repository" at "https://maven.seasar.org/maven2/",
        Resolver.bintrayRepo("segence", "maven-oss-releases"),
        Resolver.bintrayRepo("everpeace", "maven"),
        Resolver.bintrayRepo("tanukkii007", "maven"),
        Resolver.bintrayRepo("kamon-io", "snapshots")
      )
    )

  lazy val dockerCommonSettings = Seq(
    dockerBaseImage := "adoptopenjdk/openjdk8:x86_64-alpine-jdk8u191-b12",
    maintainer in Docker := "Junichi Kato <j5ik2o@gmail.com>",
    dockerUpdateLatest := true,
    bashScriptExtraDefines ++= Seq(
      "addJava -Xms${JVM_HEAP_MIN:-1024m}",
      "addJava -Xmx${JVM_HEAP_MAX:-1024m}",
      "addJava -XX:MaxMetaspaceSize=${JVM_META_MAX:-512M}",
      "addJava ${JVM_GC_OPTIONS:--XX:+UseG1GC}",
      "addJava -Dconfig.resource=${CONFIG_RESOURCE:-application.conf}",
      "addJava -Dakka.remote.startup-timeout=60s"
    )
  )

  lazy val gatlingCommonSettings = Seq(
    organization := "com.github.j5ik2o",
    version := "1.0.0-SNAPSHOT",
    scalaVersion := "2.12.8",
    scalacOptions ++= Seq(
      "-feature",
      "-deprecation",
      "-unchecked",
      "-encoding",
      "UTF-8",
      "-Xfatal-warnings",
      "-language:_",
      // Warn if an argument list is modified to match the receiver
      "-Ywarn-adapted-args",
      // Warn when dead code is identified.
      "-Ywarn-dead-code",
      // Warn about inaccessible types in method signatures.
      "-Ywarn-inaccessible",
      // Warn when a type argument is inferred to be `Any`.
      "-Ywarn-infer-any",
      // Warn when non-nullary `def f()' overrides nullary `def f'
      "-Ywarn-nullary-override",
      // Warn when nullary methods return Unit.
      "-Ywarn-nullary-unit",
      // Warn when numerics are widened.
      "-Ywarn-numeric-widen",
      // Warn when imports are unused.
      "-Ywarn-unused-import",
      "-Ywarn-numeric-widen"
    )
  )

  val apiServerEcrSettings = Seq(
    region in Ecr := Region.getRegion(Regions.AP_NORTHEAST_1),
    repositoryName in Ecr := "j5ik2o-aws-gatling-tools/api-server",
    repositoryTags in Ecr ++= Seq(version.value),
    localDockerImage in Ecr := "j5ik2o/" + (packageName in Docker).value + ":" + (version in Docker).value,
    push in Ecr := ((push in Ecr) dependsOn (publishLocal in Docker, login in Ecr)).value
  )

  lazy val gatlingRunnerEcrSettings = Seq(
    region in Ecr := Region.getRegion(Regions.AP_NORTHEAST_1),
    repositoryName in Ecr := "j5ik2o-aws-gatling-tools/gatling-runner",
    localDockerImage in Ecr := "j5ik2o/" + (packageName in Docker).value + ":" + (version in Docker).value,
    push in Ecr := ((push in Ecr) dependsOn (publishLocal in Docker, login in Ecr)).value
  )
  lazy val gatlingAggregateRunnerEcrSettings = Seq(
    region in Ecr := Region.getRegion(Regions.AP_NORTHEAST_1),
    repositoryName in Ecr := "j5ik2o-aws-gatling-tools/gatling-aggregate-runner",
    localDockerImage in Ecr := "j5ik2o/" + (packageName in Docker).value + ":" + (version in Docker).value,
    push in Ecr := ((push in Ecr) dependsOn (publishLocal in Docker, login in Ecr)).value
  )

  val gatling = taskKey[Unit]("gatling")
  val runTask = taskKey[Seq[Task]]("run-task")

  val runTaskEcsClient = settingKey[EcsAsyncClient]("run-task-ecs-client")
  val runTaskAwaitDuration = settingKey[Duration]("run-task-await-duration")
  val runTaskEcsCluster = settingKey[String]("run-task-ecs-cluster")
  val runTaskTaskDefinition = taskKey[String]("run-task-task-definition")
  val runTaskSubnets = settingKey[Seq[String]]("run-task-subnets")
  val runTaskAssignPublicIp =
    settingKey[AssignPublicIp]("run-task-assign-public-ip")
  val runTaskEnvironments =
    taskKey[Map[String, String]]("run-task-environments")
  val runTaskContainerOverrideName =
    settingKey[String]("run-task-container-override-name")

  def getTaskDefinitionName(client: EcsAsyncClient,
                            awaitDuration: Duration,
                            prefix: String): String = {
    def loop(request: ListTaskDefinitionsRequest): Future[String] = {
      client.listTaskDefinitions(request).flatMap { result =>
        if (result.sdkHttpResponse().isSuccessful) {
          result.nextTokenAsScala match {
            case None =>
              Future.successful(result.taskDefinitionArns().asScala.head)
            case Some(nextToken) =>
              val req = ListTaskDefinitionsRequest
                .builder()
                .familyPrefix(prefix)
                .nextToken(nextToken)
                .sort(SortOrder.DESC)
                .maxResults(1)
                .build()
              loop(req)
          }
        } else
          Future.failed(
            new Exception(result.sdkHttpResponse().statusText().asScala.get)
          )
      }
    }
    val req = ListTaskDefinitionsRequest
      .builder()
      .familyPrefix(prefix)
      .sort(SortOrder.DESC)
      .maxResults(1)
      .build()
    Await.result(loop(req), awaitDuration)
  }
  val gatlingAggregateRunTaskSettings = Seq(
    runTaskEcsClient in gatling := {
      val underlying = JavaEcsAsyncClient
        .builder()
        .build()
      EcsAsyncClient(underlying)
    },
    runTaskEcsCluster in gatling := sys.env
      .getOrElse("GATLING_ECS_CLUSTER", "j5ik2o-aws-gatling-tools-ecs"),
    runTaskTaskDefinition in gatling := {
      getTaskDefinitionName(
        client = (runTaskEcsClient in gatling).value,
        awaitDuration = (runTaskAwaitDuration in gatling).value,
        prefix = "j5ik2o-aws-gatling-tools-gatling-aggregate-runner"
      )
    },
    runTaskAwaitDuration in gatling := Duration.Inf,
    runTaskSubnets in gatling := Seq(
      sys.env.getOrElse("GATLING_SUBNET_ID", "subnet-XXXXXXXXX")
    ),
    runTaskAssignPublicIp in gatling := AssignPublicIp.ENABLED,
    runTaskEnvironments in gatling := {
      Map(
        "AWS_REGION" -> sys.env("AWS_REGION"),
        "GATLING_ECS_CLUSTER_NAME" -> (runTaskEcsCluster in gatling).value,
        "GATLING_SUBNET" -> (runTaskSubnets in gatling).value.head,
        "GATLING_TASK_DEFINITION" -> {
          getTaskDefinitionName(
            client = (runTaskEcsClient in gatling).value,
            awaitDuration = (runTaskAwaitDuration in gatling).value,
            prefix = "j5ik2o-aws-gatling-tools-gatling-runner"
          )
        },
        "GATLING_COUNT" -> sys.env("GATLING_COUNT"),
        "GATLING_PAUSE_DURATION" -> sys.env("GATLING_PAUSE_DURATION"),
        "GATLING_RAMP_DURATION" -> sys.env("GATLING_RAMP_DURATION"),
        "GATLING_HOLD_DURATION" -> sys.env("GATLING_HOLD_DURATION"),
        "GATLING_TARGET_ENDPOINT_BASE_URL" -> sys.env(
          "GATLING_TARGET_ENDPOINT_BASE_URL"
        ),
        "GATLING_SIMULATION_CLASS" -> sys.env("GATLING_SIMULATION_CLASS"),
        "GATLING_USERS" -> sys.env("GATLING_USERS"),
        "GATLING_REPORTER_TASK_DEFINITION" -> {
          getTaskDefinitionName(
            client = (runTaskEcsClient in gatling).value,
            awaitDuration = (runTaskAwaitDuration in gatling).value,
            prefix = "j5ik2o-aws-gatling-tools-gatling-s3-reporter"
          )
        },
        "GATLING_BUCKET_NAME" -> sys.env("GATLING_BUCKET_NAME")
      ) ++ sys.env
        .get("GATLING_NOTICE_SLACK_INCOMING_WEBHOOK_URL")
        .map(v => Map("GATLING_NOTICE_SLACK_INCOMING_WEBHOOK_URL" -> v))
        .getOrElse(Map.empty) ++ {
        sys.env
          .get("GATLING_NOTICE_CHATWORK_HOST")
          .map(v => Map("GATLING_NOTICE_CHATWORK_HOST" -> v))
          .getOrElse(Map.empty) ++
          sys.env
            .get("GATLING_NOTICE_CHATWORK_ROOM_ID")
            .map(v => Map("GATLING_NOTICE_CHATWORK_ROOM_ID" -> v))
            .getOrElse(Map.empty) ++
          sys.env
            .get("GATLING_NOTICE_CHATWORK_TOKEN")
            .map(v => Map("GATLING_NOTICE_CHATWORK_TOKEN" -> v))
            .getOrElse(Map.empty)
      }
    },
    runTaskContainerOverrideName in gatling := "gatling-aggregate-runner",
    runTask in gatling := {
      implicit val log = streams.value.log
      val _runTaskEcsClient = (runTaskEcsClient in gatling).value
      val _runTaskEcsCluster = (runTaskEcsCluster in gatling).value
      val _runTaskTaskDefinition = (runTaskTaskDefinition in gatling).value
      val _runTaskSubnets = (runTaskSubnets in gatling).value
      val _runTaskAssignPublicIp = (runTaskAssignPublicIp in gatling).value
      val _runTaskContainerOverrideName =
        (runTaskContainerOverrideName in gatling).value
      val _runTaskEnvironments = (runTaskEnvironments in gatling).value
      log.info("start runGatlingTask")
      val future = runGatlingTask(
        _runTaskEcsClient,
        _runTaskEcsCluster,
        _runTaskTaskDefinition,
        1,
        _runTaskSubnets,
        _runTaskAssignPublicIp,
        _runTaskContainerOverrideName,
        _runTaskEnvironments
      )
      val result = Await.result(future, (runTaskAwaitDuration in gatling).value)
      result.foreach { task: Task =>
        log.info(s"task.arn = ${task.taskArn()}")
      }
      log.info("finish runGatlingTask")
      result
    }
  )

  def runGatlingTask(
    runTaskEcsClient: EcsAsyncClient,
    runTaskEcsCluster: String,
    runTaskTaskDefinition: String,
    runTaskCount: Int,
    runTaskSubnets: Seq[String],
    runTaskAssignPublicIp: AssignPublicIp,
    runTaskContainerOverrideName: String,
    runTaskEnvironments: Map[String, String]
  )(implicit log: ManagedLogger): Future[Seq[Task]] = {
    val runTaskRequest = RunTaskRequest
      .builder()
      .cluster(runTaskEcsCluster)
      .taskDefinition(runTaskTaskDefinition)
      .count(runTaskCount)
      .launchType(LaunchType.FARGATE)
      .networkConfiguration(
        NetworkConfiguration
          .builder()
          .awsvpcConfiguration(
            AwsVpcConfiguration
              .builder()
              .subnets(runTaskSubnets.asJava)
              .assignPublicIp(runTaskAssignPublicIp)
              .build()
          )
          .build()
      )
      .overrides(
        TaskOverride
          .builder()
          .containerOverrides(
            ContainerOverride
              .builder()
              .name(runTaskContainerOverrideName)
              .environment(
                runTaskEnvironments
                  .map {
                    case (k, v) =>
                      KeyValuePair.builder().name(k).value(v).build()
                  }
                  .toSeq
                  .asJava
              )
              .build()
          )
          .build()
      )
      .build()
    val future = runTaskEcsClient.runTask(runTaskRequest).flatMap { result =>
      if (result.sdkHttpResponse().isSuccessful) {
        val tasks = result.tasks().asScala
        Future.successful(tasks)
      } else
        throw new Exception(
          result.failures().asScala.map(_.toString()).mkString(",")
        )
    }
    future
  }

}
