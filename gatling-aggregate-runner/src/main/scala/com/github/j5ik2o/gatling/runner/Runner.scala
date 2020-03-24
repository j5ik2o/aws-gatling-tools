package com.github.j5ik2o.gatling.runner

import java.text.SimpleDateFormat
import java.util.Date

import com.github.j5ik2o.reactive.aws.ecs.EcsAsyncClient
import com.github.j5ik2o.reactive.aws.ecs.implicits._
import com.typesafe.config.{Config, ConfigFactory}
import net.ceedubs.ficus.Ficus._
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.ecs.model._
import software.amazon.awssdk.services.ecs.{
  EcsAsyncClient => JavaEcsAsyncClient
}

import scala.collection.JavaConverters._
import scala.compat.java8.OptionConverters._
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

object Runner extends App with Loop {

  val now = new Date
  val logger = LoggerFactory.getLogger(getClass)
  val config: Config = ConfigFactory.load()

  val underlying = JavaEcsAsyncClient.builder().build()
  val ecsAsyncClient = EcsAsyncClient(underlying)

  val slackIncomingWebhookUrlOpt =
    config.getAs[String]("gatling.notice.slack.incoming-webhook-url")
  logger.info(s"incomingWebhookUrlOpt = $slackIncomingWebhookUrlOpt")

  val chatworkHostOpt = config.getAs[String]("gatling.notice.chatwork.host")
  val chatworkRoomIdOpt =
    config.getAs[String]("gatling.notice.chatwork.room-id")
  val chatworkTokenOpt = config.getAs[String]("gatling.notice.chatwork.token")
  logger.info(
    s"chatworkHostOpt = $chatworkHostOpt, chatworkRoomIdOpt = $chatworkRoomIdOpt, chatworkTokenOpt = $chatworkTokenOpt"
  )

  val runTaskEcsClusterName = config.as[String]("gatling.ecs-cluster-name")
  val runTaskTaskDefinition = config.as[String]("gatling.task-definition")
  val runTaskCount = config.as[Int]("gatling.count")
  val runTaskSubnets = config.as[Seq[String]]("gatling.subnets")
  val runTaskAssignPublicIp =
    AssignPublicIp.valueOf(config.as[String]("gatling.assign-public-ip"))

  val runTaskLogPrefix = config.as[String]("gatling.log-prefix")
  val runTaskContainerOverrideName =
    config.as[String]("gatling.container-override-name")

  val df = new SimpleDateFormat("YYYYMMDDHHmmss")
  val executionIdPath = runTaskLogPrefix + df.format(now) + "-" + now.getTime.toString

  logger.info(s"executionIdPath = $executionIdPath")

  val runTaskEnvironments = config.as[Map[String, String]](
    "gatling.environments"
  ) ++ Map("GATLING_EXECUTION_ID" -> executionIdPath)

  val runTaskReporterTaskDefinition =
    config.as[String]("gatling.reporter.task-definition")
  val runTaskReporterContainerOverrideName =
    config.as[String]("gatling.reporter.container-override-name")

  val runTaskReporterEnvironments = config.as[Map[String, String]](
    "gatling.reporter.environments"
  ) ++ Map("GATLING_RESULT_DIR_PATH" -> executionIdPath)

  import scala.concurrent.ExecutionContext.Implicits.global

  val futureTasks = if (runTaskCount > 10) {
    val n = runTaskCount / 10
    val l = runTaskCount % 10
    Future
      .traverse(Seq.fill(n)(runTaskCount) ++ (if (l > 0) Seq(l) else Seq.empty)) {
        count =>
          EcsTaskUtil.runTask(
            ecsAsyncClient,
            runTaskEcsClusterName,
            runTaskTaskDefinition,
            count,
            runTaskSubnets,
            runTaskAssignPublicIp,
            runTaskContainerOverrideName,
            runTaskEnvironments
          )
      }
      .map(_.flatten)
  } else {
    EcsTaskUtil.runTask(
      ecsAsyncClient,
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
    val message = s"Gatling Runner started: \n task arns = ${taskArns
      .map(_.split("/")(1))
      .map(getTaskUrl)
      .mkString("[\n", "\n", "\n]")}\n runTaskCount = $runTaskCount, runTaskEnvironments = $runTaskEnvironments"
    logger.info(message)
    NoticeUtil.sendMessageToChatwork(
      chatworkHostOpt,
      chatworkRoomIdOpt,
      chatworkTokenOpt,
      message
    )
    NoticeUtil.sendMessagesToSlack(slackIncomingWebhookUrlOpt, message)
    gatlingTaskLoop(taskArns)
  }

  Await.result(future, Duration.Inf)

}

trait Loop { this: Runner.type =>
  def getTaskUrl(taskArn: String): String =
    s"https://ap-northeast-1.console.aws.amazon.com/ecs/home?region=ap-northeast-1#/clusters/${runTaskEcsClusterName}/tasks/${taskArn}/details"

  def gatlingS3ReporterLoop(
    reporterTaskArns: Seq[String]
  )(implicit ec: ExecutionContext): Future[Unit] = {
    ecsAsyncClient
      .describeTasks(
        DescribeTasksRequest
          .builder()
          .cluster(runTaskEcsClusterName)
          .include(TaskField.knownValues())
          .tasksAsScala(reporterTaskArns)
          .build()
      )
      .flatMap { res =>
        val list = res.getValueForField("tasks", classOf[java.util.List[Task]])
        val reporterTasks = list.asScala.flatMap(_.asScala.toList)
        if (reporterTasks.exists(_.forall(_.lastStatus() == "STOPPED"))) {
          val bucketName = runTaskEnvironments("GATLING_S3_BUCKET_NAME")
          val message =
            s"Gatling Reporter finished: report url: https://$bucketName.s3.amazonaws.com/$executionIdPath/index.html"
          logger.info(message)
          NoticeUtil.sendMessageToChatwork(
            chatworkHostOpt,
            chatworkRoomIdOpt,
            chatworkTokenOpt,
            message
          )
          NoticeUtil.sendMessagesToSlack(slackIncomingWebhookUrlOpt, message)
          Future.successful(())
        } else {
          logger.info("---")
          Thread.sleep(1000)
          gatlingS3ReporterLoop(reporterTaskArns)
        }
      }
  }
  def gatlingTaskLoop(
    taskArns: Seq[String]
  )(implicit ec: ExecutionContext): Future[Unit] = {
    ecsAsyncClient
      .describeTasks(
        DescribeTasksRequest
          .builder()
          .cluster(runTaskEcsClusterName)
          .include(TaskField.knownValues())
          .tasksAsScala(taskArns)
          .build()
      )
      .flatMap { res =>
        val list = res.getValueForField("tasks", classOf[java.util.List[Task]])
        val tasks = list.asScala.flatMap(_.asScala.toList)
        if (tasks.exists(_.forall(_.lastStatus() == "STOPPED"))) {
          val message = s"Gatling Runner finished: task arns = ${taskArns
            .map(_.split("/")(1))
            .map(getTaskUrl)
            .mkString("[\n", "\n", "\n]")}"
          logger.info(message)
          NoticeUtil.sendMessageToChatwork(
            chatworkHostOpt,
            chatworkRoomIdOpt,
            chatworkTokenOpt,
            message
          )
          NoticeUtil.sendMessagesToSlack(slackIncomingWebhookUrlOpt, message)
          Thread.sleep(1000)
          EcsTaskUtil
            .runTask(
              ecsAsyncClient,
              runTaskEcsClusterName,
              runTaskReporterTaskDefinition,
              1,
              runTaskSubnets,
              runTaskAssignPublicIp,
              runTaskReporterContainerOverrideName,
              runTaskReporterEnvironments
            )
            .flatMap { reporterTasks =>
              val reporterTaskArns = reporterTasks.map(_.taskArn())
              val message =
                s"Gatling Reporter started: task arns = ${getTaskUrl(
                  reporterTaskArns.head.split("/")(1)
                )}\n runTaskReporterEnvironments = $runTaskReporterEnvironments"
              logger.info(message)
              NoticeUtil.sendMessageToChatwork(
                chatworkHostOpt,
                chatworkRoomIdOpt,
                chatworkTokenOpt,
                message
              )
              NoticeUtil.sendMessagesToSlack(
                slackIncomingWebhookUrlOpt,
                message
              )
              gatlingS3ReporterLoop(reporterTaskArns)
            }
        } else {
          logger.info("---")
          Thread.sleep(1000)
          gatlingTaskLoop(taskArns)
        }
      }
  }
}
