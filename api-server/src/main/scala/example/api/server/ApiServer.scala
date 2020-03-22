package example.api.server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._

import scala.concurrent.Await
import scala.concurrent.duration._

object ApiServer extends App {
  implicit val system = ActorSystem("my-system")
  implicit val executionContext = system.dispatcher

  val route =
    path("hello") {
      get {
        complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, "ok"))
      }
    }

  val bindingFuture = Http().bindAndHandle(route, "0.0.0.0", 80).map {
    serverBinding =>
      system.log.info(s"Server online at ${serverBinding.localAddress}")
      serverBinding
  }

  sys.addShutdownHook {
    val future = bindingFuture
      .flatMap { serverBinding =>
        serverBinding.unbind()
      }
      .flatMap { _ =>
        system.terminate()
      }
    Await.result(future, 5 seconds)
  }
}
