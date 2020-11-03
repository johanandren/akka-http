/*
 * Copyright (C) 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.http.impl.engine.http2

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.HttpEncodings
import akka.http.scaladsl.model.{ AttributeKey, HttpRequest, HttpResponse, RequestResponseAssociation, headers }
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{ Flow, Sink, Source }
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._
import scala.concurrent.{ Future, Promise }

/** A small example app that shows how to use the HTTP/2 client API currently against actual internet servers */
// needs to live in impl.engine.http2 for now as we have no public access to the internal HTTP2 client
object Http2ClientApp extends App {
  val config =
    ConfigFactory.parseString(
      """
         # akka.loglevel = debug
         akka.http.client.http2.log-frames = true
         akka.http.client.parsing.max-content-length = 20m
      """
    ).withFallback(ConfigFactory.defaultApplication())

  implicit val system = ActorSystem("Http2ClientApp", config)
  implicit val ec = system.dispatcher

  val dispatch = singleRequest(Http().connectionTo("doc.akka.io").http2().connectionFlow())

  dispatch(
    HttpRequest(
      uri = "https://doc.akka.io/api/akka/current/akka/actor/typed/scaladsl/index.html",
      headers = headers.`Accept-Encoding`(HttpEncodings.gzip) :: Nil)
  ).map { res =>
      println(s"[1] Got index.html: $res")
      res.entity.dataBytes.runWith(Sink.ignore).onComplete(res => println(s"Finished reading [1] $res"))
    }
  dispatch(
    HttpRequest(
      uri = "https://doc.akka.io/api/akka/current/index.js",
      headers = /*headers.`Accept-Encoding`(HttpEncodings.gzip) ::*/ Nil)
  ).onComplete { res =>
      println(s"[2] Got index.js: $res")
      res.get.entity.dataBytes.runWith(Sink.ignore).onComplete(res => println(s"Finished reading [2] $res"))
    }
  dispatch(HttpRequest(uri = "https://doc.akka.io/api/akka/current/lib/MaterialIcons-Regular.woff"))
    .flatMap(_.toStrict(1.second))
    .onComplete(res => println(s"[3] Got font: $res"))
  dispatch(HttpRequest(uri = "https://doc.akka.io/favicon.ico"))
    .flatMap(_.toStrict(1.second))
    .onComplete(res => println(s"[4] Got favicon: $res"))

  def singleRequest(connection: Flow[HttpRequest, HttpResponse, Any], bufferSize: Int = 100): HttpRequest => Future[HttpResponse] = {
    val queue =
      Source.queue(bufferSize, OverflowStrategy.dropNew)
        .via(connection)
        .to(Sink.foreach { res =>
          res.attribute(ResponsePromise.Key).get.promise.trySuccess(res)
        })
        .run()

    req => {
      val p = Promise[HttpResponse]()
      queue.offer(req.addAttribute(ResponsePromise.Key, ResponsePromise(p)))
        .flatMap(_ => p.future)
    }
  }
}

// FIXME should we provide this out of the box perhaps?
case class ResponsePromise(promise: Promise[HttpResponse]) extends RequestResponseAssociation
object ResponsePromise {
  val Key = AttributeKey[ResponsePromise]("association-handle")
}
