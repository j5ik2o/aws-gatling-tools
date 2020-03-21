package com.github.j5ik2o.gatling.runner

import java.io.File

import akka.actor.ReflectiveDynamicAccess
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.{ AmazonS3, AmazonS3ClientBuilder }
import com.typesafe.config.ConfigFactory
import io.gatling.app.Gatling
import io.gatling.core.scenario.Simulation
import org.slf4j.LoggerFactory

import scala.collection.mutable

object Runner extends App {

  val logger = LoggerFactory.getLogger(getClass)

  val config              = ConfigFactory.load()
  val simulationClassName = config.getString("thread-weaver.gatling.simulation-classname")
  val executionId         = config.getString("thread-weaver.gatling.execution-id")

  val s3EndPoint = {
    val endPoint = config.getString("thread-weaver.gatling.aws-s3-endpoint")
    if (endPoint.isEmpty) None else Some(endPoint)
  }
  val bucketName          = config.getString("thread-weaver.gatling.aws-s3-bucket-name")
  val createBucketOnStart = config.getBoolean("thread-weaver.gatling.aws-s3-create-bucket-on-start")
  val pathStyleAccess     = config.getBoolean("thread-weaver.gatling.aws-s3-path-style-access")

  val gatlingConfig = ConfigFactory.load("gatling.conf")
  val gatlingDir    = gatlingConfig.getString("gatling.core.directory.results")

  val dynamic                       = new ReflectiveDynamicAccess(getClass.getClassLoader)
  val clazz: Class[_ <: Simulation] = dynamic.getClassFor[Simulation](simulationClassName).get
  val simulationName                = clazz.getSimpleName

  logger.info(s"Simulation class is: ${clazz.getCanonicalName}")
  logger.info(s"Simulation name is: $simulationName")

  val client: AmazonS3 = {
    val builder = AmazonS3ClientBuilder.standard()
    s3EndPoint match {
      case Some(endpoint) => // for debug
        builder
          .withEndpointConfiguration(new EndpointConfiguration(endpoint, Regions.AP_NORTHEAST_1.name()))
          .withPathStyleAccessEnabled(pathStyleAccess)
          .build()
      case None => // for production
        builder.build()
    }
  }

  // @see io.gatling.app.cli.ArgsParser.parseArguments
  Gatling.fromMap(mutable.Map("gatling.core.simulationClass" -> clazz.getCanonicalName))

  if (createBucketOnStart) client.createBucket(bucketName)

  val latestTimestamp = new File(gatlingDir).listFiles().map(_.getName.split("-")(1).toLong).max
  val targetLogFile   = s"$gatlingDir/${simulationName.toLowerCase()}-$latestTimestamp/simulation.log"

  logger.info("generated gatling log file is " + targetLogFile)

  val keyName = s"$executionId/${java.util.UUID.randomUUID()}.log"
  logger.info(s"sending to bucket `$bucketName` with key `$keyName`")

  client.putObject(bucketName, keyName, new File(targetLogFile))

  val currentLogCount = client.listObjects(bucketName, executionId).getObjectSummaries.size()
  logger.info(s"the number of logs accumulated sor far: $currentLogCount")

}
