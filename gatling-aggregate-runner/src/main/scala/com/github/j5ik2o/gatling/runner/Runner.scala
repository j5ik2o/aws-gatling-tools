package com.github.j5ik2o.gatling.runner

import java.text.SimpleDateFormat
import java.util.Date

import com.github.j5ik2o.reactive.aws.ecs.EcsAsyncClient
import com.github.j5ik2o.reactive.aws.ecs.implicits._
import com.typesafe.config.{ Config, ConfigFactory }
import net.ceedubs.ficus.Ficus._
import org.slf4j.LoggerFactory
import scalaj.http.{ Http, HttpResponse }
import software.amazon.awssdk.services.ecs.model._
import software.amazon.awssdk.services.ecs.{ EcsAsyncClient => JavaEcsAsyncClient }

import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters._
import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, ExecutionContext, Future }

object Runner extends App {

  def sendMessageToSlack(incomingWebhook: String, message: String): HttpResponse[String] = {
    Http(incomingWebhook)
      .header("Content-type", "application/json")
      .postData("{\"text\": \"" + message + "\"}").asString
  }

//  def sendMessageToChatwork(message: String, roomId: String, host: String, token: String): HttpResponse[String] = {
//    val url = s"$host/v2/rooms/$roomId/messages"
//    logger.info(s"sending url = $url")
//    Http(url)
//      .header("X-ChatWorkToken", token).postForm(Seq("body" -> message, "self_unread" -> "0")).asString
//  }

  def runTask(
      runTaskEcsClient: EcsAsyncClient,
      runTaskEcsClusterName: String,
      runTaskTaskDefinition: String,
      runTaskCount: Int,
      runTaskSubnets: Seq[String],
      runTaskAssignPublicIp: AssignPublicIp,
      runTaskContainerOverrideName: String,
      runTaskEnvironments: Map[String, String]
  )(implicit ec: ExecutionContext): Future[Seq[Task]] = {
    val runTaskRequest = RunTaskRequest
      .builder()
      .cluster(runTaskEcsClusterName)
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

  val now            = new Date
  val logger         = LoggerFactory.getLogger(getClass)
  val config: Config = ConfigFactory.load()

  val underlying            = JavaEcsAsyncClient.builder().build()
  val client                = EcsAsyncClient(underlying)
  val incomingWebhookUrlOpt = config.getAs[String]("gatling.notice.slack.incoming-webhook-url")
  logger.info(s"incomingWebhookUrlOpt = $incomingWebhookUrlOpt")

  val runTaskEcsClusterName = config.as[String]("gatling.ecs-cluster-name")
  val runTaskTaskDefinition = config.as[String]("gatling.task-definition")
  val runTaskCount          = config.as[Int]("gatling.count")
  val runTaskSubnets        = config.as[Seq[String]]("gatling.subnets")
  val runTaskAssignPublicIp = AssignPublicIp.valueOf(config.as[String]("gatling.assign-public-ip"))

  val runTaskLogPrefix             = config.as[String]("gatling.log-prefix")
  val runTaskContainerOverrideName = config.as[String]("gatling.container-override-name")

  val df              = new SimpleDateFormat("YYYYMMDDHHmmss")
  val executionIdPath = runTaskLogPrefix + df.format(now) + "-" + now.getTime.toString

  logger.info(s"executionIdPath = $executionIdPath")

  val runTaskEnvironments = config.as[Map[String, String]]("gatling.environments") ++ Map(
      "TW_GATLING_EXECUTION_ID" -> executionIdPath
    )

  val runTaskReporterTaskDefinition        = config.as[String]("gatling.reporter.task-definition")
  val runTaskReporterContainerOverrideName = config.as[String]("gatling.reporter.container-override-name")

  val runTaskReporterEnvironments = config.as[Map[String, String]]("gatling.reporter.environments") ++ Map(
      "TW_GATLING_RESULT_DIR_PATH" -> executionIdPath
    )

  import scala.concurrent.ExecutionContext.Implicits.global

  def gatlingS3ReporterLoop(reporterTaskArns: Seq[String]): Future[Unit] = {
    client
      .describeTasks(
        DescribeTasksRequest
          .builder().cluster(runTaskEcsClusterName).include(TaskField.knownValues()).tasksAsScala(
            reporterTaskArns
          ).build()
      ).flatMap { res =>
        val list          = res.getValueForField("tasks", classOf[java.util.List[Task]])
        val reporterTasks = list.asScala.flatMap(_.asScala.toList)
        if (reporterTasks.exists(_.forall(_.lastStatus() == "STOPPED"))) {
          val bucketName = runTaskEnvironments("TW_GATLING_S3_BUCKET_NAME")
          logger.info(
            s"Gatling Reporter finished: report url: https://$bucketName.s3.amazonaws.com/$executionIdPath/index.html"
          )
          incomingWebhookUrlOpt.foreach { incomingWebhookUrl =>
            val response = sendMessageToSlack(
              incomingWebhookUrl,
              s"Gatling Reporter finished: report url: https://$bucketName.s3.amazonaws.com/$executionIdPath/index.html"
            )
            logger.info(s"sendMessage.response = $response")
          }
          Future.successful(())
        } else {
          logger.info("---")
          Thread.sleep(1000)
          gatlingS3ReporterLoop(reporterTaskArns)
        }
      }
  }

  def getTaskUrl(taskArn: String): String =
    s"https://ap-northeast-1.console.aws.amazon.com/ecs/home?region=ap-northeast-1#/clusters/${runTaskEcsClusterName}/tasks/${taskArn}/details"

  def gatlingTaskLoop(taskArns: Seq[String]): Future[Unit] = {
    client
      .describeTasks(
        DescribeTasksRequest
          .builder().cluster(runTaskEcsClusterName).include(TaskField.knownValues()).tasksAsScala(taskArns).build()
      ).flatMap { res =>
        val list  = res.getValueForField("tasks", classOf[java.util.List[Task]])
        val tasks = list.asScala.flatMap(_.asScala.toList)
        if (tasks.exists(_.forall(_.lastStatus() == "STOPPED"))) {
          logger.info(
            s"Gatling Runner finished: task arns = ${taskArns.map(_.split("/")(1)).map(getTaskUrl).mkString(",")}"
          )
          incomingWebhookUrlOpt.foreach { incomingWebhookUrl =>
            val response = sendMessageToSlack(
              incomingWebhookUrl,
              s"Gatling Runner finished: task arns = ${taskArns
                .map(_.split("/")(1)).map(getTaskUrl).mkString("[\n", "\n", "\n]")}"
            )
            logger.info(s"sendMessage.response = $response")
          }
          Thread.sleep(1000)
          runTask(
            client,
            runTaskEcsClusterName,
            runTaskReporterTaskDefinition,
            1,
            runTaskSubnets,
            runTaskAssignPublicIp,
            runTaskReporterContainerOverrideName,
            runTaskReporterEnvironments
          ).flatMap { reporterTasks =>
            val reporterTaskArns = reporterTasks.map(_.taskArn())
            logger.info(
              s"Gatling Reporter started: task arn = ${getTaskUrl(reporterTaskArns.head.split("/")(1))}\n runTaskReporterEnvironments = $runTaskReporterEnvironments"
            )
            incomingWebhookUrlOpt.foreach { incomingWebhookUrl =>
              val response = sendMessageToSlack(
                incomingWebhookUrl,
                s"Gatling Reporter started: task arns = ${getTaskUrl(reporterTaskArns.head.split("/")(1))}\n runTaskReporterEnvironments = $runTaskReporterEnvironments"
              )
              logger.info(s"sendMessage.response = $response")
            }
            gatlingS3ReporterLoop(reporterTaskArns)
          }
        } else {
          logger.info("---")
          Thread.sleep(1000)
          gatlingTaskLoop(taskArns)
        }
      }
  }

  val futureTasks = if (runTaskCount > 10) {
    val n = runTaskCount / 10
    val l = runTaskCount % 10
    Future
      .traverse(Seq.fill(n)(runTaskCount) ++ (if (l > 0) Seq(l) else Seq.empty)) { count =>
        runTask(
          client,
          runTaskEcsClusterName,
          runTaskTaskDefinition,
          count,
          runTaskSubnets,
          runTaskAssignPublicIp,
          runTaskContainerOverrideName,
          runTaskEnvironments
        )
      }.map(_.flatten)
  } else {
    runTask(
      client,
      runTaskEcsClusterName,
      runTaskTaskDefinition,
      runTaskCount,
      runTaskSubnets,
      runTaskAssignPublicIp,
      runTaskContainerOverrideName,
      runTaskEnvironments
    )
  }

  val future = futureTasks.flatMap { tasks =>
    val taskArns = tasks.map(_.taskArn())
    logger.info(
      s"Gatling Runner started: task arns = ${taskArns.map(_.split("/")(1)).map(getTaskUrl).mkString(",")}\n runTaskEnvironments = $runTaskEnvironments"
    )
    incomingWebhookUrlOpt.foreach { incomingWebhookUrl =>
      val response = sendMessageToSlack(
        incomingWebhookUrl,
        s"Gatling Runner started: \n task arns = ${taskArns
          .map(_.split("/")(1)).map(getTaskUrl).mkString("[\n", "\n", "\n]")}\n runTaskCount = $runTaskCount, runTaskEnvironments = $runTaskEnvironments"
      )
      logger.info(s"sendMessage.response = $response")
    }
    gatlingTaskLoop(taskArns)
  }
  Await.result(future, Duration.Inf)
}
