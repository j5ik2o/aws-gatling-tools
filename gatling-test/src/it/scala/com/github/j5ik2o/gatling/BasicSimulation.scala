package com.github.j5ik2o.gatling

import com.typesafe.config.ConfigFactory
import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.http.protocol.HttpProtocolBuilder
import io.gatling.http.request.builder.HttpRequestBuilder

import scala.concurrent.duration._

class BasicSimulation extends Simulation {
  private val config = ConfigFactory.load()
  private val endpoint =
    config.getString("runner.gatling.target-endpoint-base-url")
  private val pauseDuration =
    config.getDuration("runner.gatling.pause-duration").toMillis.millis
  private val numOfUser = config.getInt("runner.gatling.users")
  private val rampDuration =
    config.getDuration("runner.gatling.ramp-duration").toMillis.millis
  private val holdDuration =
    config.getDuration("runner.gatling.hold-duration").toMillis.millis
  private val entireDuration = rampDuration + holdDuration

  private val httpConf: HttpProtocolBuilder =
    http.userAgentHeader(
      "Mozilla/5.0 (Windows NT 5.1; rv:31.0) Gecko/20100101 Firefox/31.0"
    )

  val scn = scenario(getClass.getName)
    .forever {
      pause(pauseDuration).exec(hello)
    }

  setUp(scn.inject(rampUsers(numOfUser).during(rampDuration)))
    .protocols(httpConf)
    .maxDuration(entireDuration)

  private def hello: HttpRequestBuilder = {
    http("hello")
      .get(s"$endpoint/hello")
      .check(status.is(200))
  }

}
