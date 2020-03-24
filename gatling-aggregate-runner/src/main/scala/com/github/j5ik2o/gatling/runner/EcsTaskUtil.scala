package com.github.j5ik2o.gatling.runner

import com.github.j5ik2o.reactive.aws.ecs.EcsAsyncClient
import software.amazon.awssdk.services.ecs.model._

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

object EcsTaskUtil {
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
