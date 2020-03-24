package com.github.j5ik2o.gatling.runner

import org.slf4j.{Logger, LoggerFactory}
import scalaj.http.{Http, HttpResponse}

object NoticeUtil {

  val logger: Logger = LoggerFactory.getLogger(getClass)

  def sendMessageToSlack(incomingWebhook: String,
                         message: String): HttpResponse[String] = {
    Http(incomingWebhook)
      .header("Content-type", "application/json")
      .postData("{\"text\": \"" + message + "\"}")
      .asString
  }

  def sendMessageToChatwork(roomId: String,
                            host: String,
                            token: String,
                            message: String): HttpResponse[String] = {
    val url = s"$host/v2/rooms/$roomId/messages"
    logger.info(s"sending url = $url")
    Http(url)
      .header("X-ChatWorkToken", token)
      .postForm(Seq("body" -> message, "self_unread" -> "0"))
      .asString
  }
  def sendMessageToChatwork(hostOpt: Option[String],
                            roomIdOpt: Option[String],
                            tokenOpt: Option[String],
                            message: String): Unit = {
    (hostOpt, roomIdOpt, tokenOpt) match {
      case (Some(h), Some(r), Some(t))
          if h.nonEmpty && r.nonEmpty && t.nonEmpty =>
        val response = sendMessageToChatwork(h, r, t, message)
        logger.info(s"sendMessageToChatwork.response = $response")
      case _ =>
    }
  }

  def sendMessagesToSlack(incomingWebhookUrlOpt: Option[String],
                          message: String): Unit = {
    incomingWebhookUrlOpt match {
      case Some(incomingWebhookUrl) if incomingWebhookUrl.nonEmpty =>
        val response = sendMessageToSlack(incomingWebhookUrl, message)
        logger.info(s"sendMessageToSlack.response = $response")
      case _ =>
    }
  }
}
