import com.amazonaws.regions.{Region, Regions}
import com.github.j5ik2o.reactive.aws.ecs._
import com.github.j5ik2o.reactive.aws.ecs.implicits._
import com.typesafe.sbt.SbtNativePackager.autoImport._
import com.typesafe.sbt.packager.archetypes.scripts.BashStartScriptPlugin.autoImport._
import com.typesafe.sbt.packager.docker.Cmd
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport._
import sbt.Keys._
import sbt._
import sbt.internal.util.ManagedLogger
import sbtecr.EcrPlugin.autoImport._
import software.amazon.awssdk.services.ecs.model.{Task, _}
import software.amazon.awssdk.services.ecs.{EcsAsyncClient => JavaEcsAsyncClient}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

val gatlingVersion = "3.1.0"
val circeVersion = "0.11.1"
val awsSdkVersion = "1.11.575"

val baseSettings = Seq(
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
  ),
)

val ecrSettings = Seq(
  region in Ecr := Region.getRegion(Regions.AP_NORTHEAST_1),
  repositoryName in Ecr := "j5ik2o/api-server",
  repositoryTags in Ecr ++= Seq(version.value),
  localDockerImage in Ecr := "j5ik2o/" + (packageName in Docker).value + ":" + (version in Docker).value,
  push in Ecr := ((push in Ecr) dependsOn (publishLocal in Docker, login in Ecr)).value
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

val `api-server` = (project in file("api-server"))
  .enablePlugins(AshScriptPlugin, JavaAgent, EcrPlugin)
  .settings(baseSettings)
  .settings(dockerCommonSettings)
  .settings(ecrSettings)
  .settings(
    name := "api-server",
    mainClass in (Compile, run) := Some("example.api.server.ApiServer"),
    mainClass in reStart := Some("example.api.server.ApiServer"),
    dockerEntrypoint := Seq("/opt/docker/bin/api-server"),
    dockerUsername := Some("j5ik2o"),
    fork in run := true,
    javaAgents += "org.aspectj"            % "aspectjweaver"    % "1.8.13",
    javaAgents += "org.mortbay.jetty.alpn" % "jetty-alpn-agent" % "2.0.9" % "runtime;test",
    javaOptions in Universal += "-Dorg.aspectj.tracing.factory=default",
    javaOptions in run ++= Seq(
      s"-Dcom.sun.management.jmxremote.port=${sys.env.getOrElse("JMX_PORT", "8999")}",
      "-Dcom.sun.management.jmxremote.authenticate=false",
      "-Dcom.sun.management.jmxremote.ssl=false",
      "-Dcom.sun.management.jmxremote.local.only=false",
      "-Dcom.sun.management.jmxremote"
    ),
    javaOptions in Universal ++= Seq(
      "-Dcom.sun.management.jmxremote",
      "-Dcom.sun.management.jmxremote.local.only=true",
      "-Dcom.sun.management.jmxremote.authenticate=false"
    ),
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http" % "10.1.11",
      "com.typesafe.akka" %% "akka-stream" % "2.6.4",
      "com.github.scopt"     %% "scopt"                   % "4.0.0-RC2",
      "net.logstash.logback" % "logstash-logback-encoder" % "4.11" excludeAll (
        ExclusionRule(organization = "com.fasterxml.jackson.core", name = "jackson-core"),
        ExclusionRule(organization = "com.fasterxml.jackson.core", name = "jackson-databind")
      ),
      "org.slf4j"                     % "jul-to-slf4j"                       % "1.7.26",
      "ch.qos.logback"                % "logback-classic"                    % "1.2.3"
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

lazy val `gatling-test` = (project in file("gatling-test"))
  .enablePlugins(GatlingPlugin)
  .settings(gatlingCommonSettings)
  .settings(
    name := "api-server-gatling-test",
    libraryDependencies ++= Seq(
      "io.gatling.highcharts" % "gatling-charts-highcharts" % gatlingVersion,
      "io.gatling" % "gatling-test-framework" % gatlingVersion,
      "com.amazonaws" % "aws-java-sdk-core" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-s3" % awsSdkVersion,
      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion
    ),
    publishArtifact in (GatlingIt, packageBin) := true
  )
  .settings(
    addArtifact(artifact in (GatlingIt, packageBin), packageBin in GatlingIt)
  )

lazy val gatlingRunnerEcrSettings = Seq(
  region in Ecr := Region.getRegion(Regions.AP_NORTHEAST_1),
  repositoryName in Ecr := "j5ik2o/api-server-gatling-runner",
  localDockerImage in Ecr := "j5ik2o/" + (packageName in Docker).value + ":" + (version in Docker).value,
  push in Ecr := ((push in Ecr) dependsOn (publishLocal in Docker, login in Ecr)).value
)

lazy val `gatling-runner` = (project in file("gatling-runner"))
  .enablePlugins(JavaAppPackaging, EcrPlugin)
  .settings(gatlingCommonSettings)
  .settings(gatlingRunnerEcrSettings)
  .settings(
    name := "api-server-gatling-runner",
    libraryDependencies ++= Seq(
      "io.gatling" % "gatling-app" % gatlingVersion,
      "com.amazonaws" % "aws-java-sdk-core" % awsSdkVersion,
      "com.amazonaws" % "aws-java-sdk-s3" % awsSdkVersion
    ),
    mainClass in (Compile, bashScriptDefines) := Some(
      "com.github.j5ik2o.gatling.runner.Runner"
    ),
    dockerBaseImage := "openjdk:8",
    dockerUsername := Some("j5ik2o"),
    packageName in Docker := "api-server-gatling-runner",
    dockerUpdateLatest := true,
    dockerCommands ++= Seq(
      Cmd("USER", "root"),
      Cmd("RUN", "mkdir /var/log/gatling"),
      Cmd("RUN", "chown daemon:daemon /var/log/gatling"),
      Cmd("ENV", "TW_GATLING_RESULT_DIR=/var/log/gatling")
    )
  )
  .dependsOn(`gatling-test` % "compile->gatling-it")

lazy val `gatling-s3-reporter` = (project in file("gatling-s3-reporter"))
  .settings(name := "api-server-gatling-s3-reporter")

lazy val gatlingAggregateRunnerEcrSettings = Seq(
  region in Ecr := Region.getRegion(Regions.AP_NORTHEAST_1),
  repositoryName in Ecr := "j5ik2o/api-server-gatling-aggregate-runner",
  localDockerImage in Ecr := "j5ik2o/" + (packageName in Docker).value + ":" + (version in Docker).value,
  push in Ecr := ((push in Ecr) dependsOn (publishLocal in Docker, login in Ecr)).value
)

val gatling = taskKey[Unit]("gatling")
val runTask = taskKey[Seq[Task]]("run-task")

val runTaskEcsClient             = settingKey[EcsAsyncClient]("run-task-ecs-client")
val runTaskAwaitDuration         = settingKey[Duration]("run-task-await-duration")
val runTaskEcsCluster            = settingKey[String]("run-task-ecs-cluster")
val runTaskTaskDefinition        = taskKey[String]("run-task-task-definition")
val runTaskSubnets               = settingKey[Seq[String]]("run-task-subnets")
val runTaskAssignPublicIp        = settingKey[AssignPublicIp]("run-task-assign-public-ip")
val runTaskEnvironments          = taskKey[Map[String, String]]("run-task-environments")
val runTaskContainerOverrideName = settingKey[String]("run-task-container-override-name")

def getTaskDefinitionName(client: EcsAsyncClient, awaitDuration: Duration, prefix: String): String = {
  def loop(request: ListTaskDefinitionsRequest): Future[String] = {
    client.listTaskDefinitions(request).flatMap { result =>
      if (result.sdkHttpResponse().isSuccessful) {
        result.nextTokenAsScala match {
          case None => Future.successful(result.taskDefinitionArns().asScala.head)
          case Some(nextToken) =>
            val req = ListTaskDefinitionsRequest
              .builder().familyPrefix(prefix).nextToken(nextToken).sort(SortOrder.DESC).maxResults(1).build()
            loop(req)
        }
      } else
        Future.failed(new Exception(result.sdkHttpResponse().statusText().asScala.get))
    }
  }
  val req = ListTaskDefinitionsRequest.builder().familyPrefix(prefix).sort(SortOrder.DESC).maxResults(1).build()
  Await.result(loop(req), awaitDuration)
}
val gatlingAggregateRunTaskSettings = Seq(
  runTaskEcsClient in gatling := {
    val underlying = JavaEcsAsyncClient
      .builder()
      .build()
    EcsAsyncClient(underlying)
  },
  runTaskEcsCluster in gatling := "j5ik2o-gatling-ecs",
  runTaskTaskDefinition in gatling := {
    getTaskDefinitionName(
      (runTaskEcsClient in gatling).value,
      (runTaskAwaitDuration in gatling).value,
      "j5ik2o-gatling-aggregate-runner"
    )
  },
  runTaskAwaitDuration in gatling := Duration.Inf,
  runTaskSubnets in gatling := Seq("subnet-096d7af9e31f4f8c7"), // 10.0.1.0/24 public
  runTaskAssignPublicIp in gatling := AssignPublicIp.ENABLED,
  runTaskEnvironments in gatling := Map(
    "AWS_REGION"                                   -> "ap-northeast-1",
    "TW_GATLING_NOTICE_SLACK_INCOMING_WEBHOOK_URL" -> sys.env("TW_GATLING_NOTICE_SLACK_INCOMING_WEBHOOK_URL"),
    "TW_GATLING_ECS_CLUSTER_NAME"                  -> (runTaskEcsCluster in gatling).value,
    "TW_GATLING_SUBNET"                            -> (runTaskSubnets in gatling).value.head,
    "TW_GATLING_TASK_DEFINITION" -> {
      getTaskDefinitionName(
        (runTaskEcsClient in gatling).value,
        (runTaskAwaitDuration in gatling).value,
        "j5ik2o-gatling-runner"
      )
    },
    "TW_GATLING_COUNT"                    -> "10",
    "TW_GATLING_PAUSE_DURATION"           -> "3s",
    "TW_GATLING_RAMP_DURATION"            -> "200s",
    "TW_GATLING_HOLD_DURATION"            -> "5m",
    "TW_GATLING_TARGET_ENDPOINT_BASE_URL" -> s"http://${sys.env("TW_GATLING_TARGET_HOST")}:8080/v1",
    "TW_GATLING_SIMULATION_CLASS"         -> "com.github.j5ik2o.gatling.BasicSimulation",
    "TW_GATLING_USERS"                    -> "10",
    "TW_GATLING_REPORTER_TASK_DEFINITION" -> {
      getTaskDefinitionName(
        (runTaskEcsClient in gatling).value,
        (runTaskAwaitDuration in gatling).value,
        "j5ik2o-gatling-s3-reporter"
      )
    },
    "TW_GATLING_BUCKET_NAME" -> "api-server-gatling-logs"
  ),
  runTaskContainerOverrideName in gatling := "gatling-aggregate-runner",
  runTask in gatling := {
    implicit val log                  = streams.value.log
    val _runTaskEcsClient             = (runTaskEcsClient in gatling).value
    val _runTaskEcsCluster            = (runTaskEcsCluster in gatling).value
    val _runTaskTaskDefinition        = (runTaskTaskDefinition in gatling).value
    val _runTaskSubnets               = (runTaskSubnets in gatling).value
    val _runTaskAssignPublicIp        = (runTaskAssignPublicIp in gatling).value
    val _runTaskContainerOverrideName = (runTaskContainerOverrideName in gatling).value
    val _runTaskEnvironments          = (runTaskEnvironments in gatling).value
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
        .builder().awsvpcConfiguration(
        AwsVpcConfiguration
          .builder()
          .subnets(runTaskSubnets.asJava)
          .assignPublicIp(runTaskAssignPublicIp)
          .build()
      ).build()
    )
    .overrides(
      TaskOverride
        .builder().containerOverrides(
        ContainerOverride
          .builder()
          .name(runTaskContainerOverrideName)
          .environment(
            runTaskEnvironments.map { case (k, v) => KeyValuePair.builder().name(k).value(v).build() }.toSeq.asJava
          ).build()
      ).build()
    )
    .build()
  val future = runTaskEcsClient.runTask(runTaskRequest).flatMap { result =>
    if (result.sdkHttpResponse().isSuccessful) {
      val tasks = result.tasks().asScala
      Future.successful(tasks)
    } else
      throw new Exception(result.failures().asScala.map(_.toString()).mkString(","))
  }
  future
}

lazy val `gatling-aggregate-runner` = (project in file("gatling-aggregate-runner"))
  .enablePlugins(JavaAppPackaging, EcrPlugin)
  .settings(gatlingCommonSettings)
  .settings(gatlingAggregateRunnerEcrSettings)
  .settings(gatlingAggregateRunTaskSettings)
  .settings(
    name := "api-server-gatling-aggregate-runner",
    mainClass in (Compile, bashScriptDefines) := Some("com.github.j5ik2o.gatling.runner.Runner"),
    dockerBaseImage := "openjdk:8",
    dockerUsername := Some("j5ik2o"),
    packageName in Docker := "api-server-gatling-aggregate-runner",
    dockerUpdateLatest := true,
    libraryDependencies ++= Seq(
      "org.slf4j"           % "slf4j-api"              % "1.7.26",
      "ch.qos.logback"      % "logback-classic"        % "1.2.3",
      "org.codehaus.janino" % "janino"                 % "3.0.6",
      "com.iheart"          %% "ficus"                 % "1.4.6",
      "com.github.j5ik2o"   %% "reactive-aws-ecs-core" % "1.1.3",
      "org.scalaj"          %% "scalaj-http"           % "2.4.2"
    )
  )

val `aws-gatling-tools` =
  (project in file("."))
    .settings(baseSettings)
    .settings(name := "aws-gatling-tools")
    .aggregate(
      `api-server`,
      `gatling-test`,
      `gatling-runner`,
      `gatling-s3-reporter`,
      `gatling-aggregate-runner`
    )
