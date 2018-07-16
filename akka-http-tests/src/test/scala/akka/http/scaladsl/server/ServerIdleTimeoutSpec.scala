/*
 * Copyright (C) 2009-2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.http.scaladsl.server

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import akka.testkit.{ SocketUtil, TestKit }
import akka.util.ByteString
import com.typesafe.config.{ Config, ConfigFactory }
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpec }

import scala.concurrent.duration._
import scala.concurrent.Promise

class ServerIdleTimeoutSpec extends WordSpec with Matchers with RequestBuilding with ScalaFutures with BeforeAndAfterAll {

  val testConf: Config = ConfigFactory.parseString("""
    akka.http.server.idle-timeout = 500ms
  """)
  implicit val system = ActorSystem(getClass.getSimpleName, testConf)
  import system.dispatcher
  implicit val materializer = ActorMaterializer()

  "The server idle timeout" must {

    "fail the entity stream" in {
      val streamResult = Promise[Done]()

      val route =
        path("upload") {
          extractRequestEntity { entity ⇒
            complete {
              val done = entity.dataBytes.map { data ⇒
                // simulate CPU intensive task like encryption, encoding, etc.
                Thread.sleep(1000)
                data
              }.runWith(Sink.ignore)
              streamResult.completeWith(done)
              done.map(_ ⇒ "ok")
            }
          }
        }

      val (hostName, port) = SocketUtil.temporaryServerHostnameAndPort()
      Http().bindAndHandle(route, hostName, port).futureValue

      // tests ------------------------------------------------------------

      val neverEndingPost = Post(
        s"http://$hostName:$port/upload",
        HttpEntity(
          ContentTypes.`application/octet-stream`,
          Source.repeat(ByteString("abcdefghijklmnopqrst"))))

      val response = Http().singleRequest(neverEndingPost)

      // should fail
      response.failed.futureValue(Timeout(3.seconds))
      // and the internal stream should also fail so that user can act on the stream not
      // being successfully consumed
      streamResult.future.failed.futureValue(Timeout(3.seconds))

    }

  }

  override protected def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }
}
