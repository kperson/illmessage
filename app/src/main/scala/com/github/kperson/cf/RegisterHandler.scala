package com.github.kperson.cf

import java.io.{InputStream, OutputStream}
import java.net.URI
import java.net.http.{HttpClient, HttpRequest}
import java.net.http.HttpRequest.BodyPublishers
import java.nio.charset.StandardCharsets
import java.util.UUID

import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}
import com.github.kperson.app.AppInit
import com.github.kperson.aws.AWSHttp._
import com.github.kperson.aws.AWSHttpResponse
import com.github.kperson.serialization._
import com.github.kperson.subscription.SubscriptionDAO
import play.api.libs.json.Json

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._


//https://docs.aws.amazon.com/AWSCloudFormation/latest/UserGuide/template-custom-resources.html
trait RegisterHandler extends RequestStreamHandler {

  implicit val ec: ExecutionContext

  def accountId: String
  def subscriptionDAO: SubscriptionDAO
  def cfRegisterDAO: CFRegisterDAO


  private val client = HttpClient.newHttpClient()


  def runRequest(method: String, url: String, body: Array[Byte] = Array.emptyByteArray, headers: Map[String, String] = Map.empty): Future[AWSHttpResponse[Array[Byte]]] = {
    val builder = HttpRequest.newBuilder(new URI(url))
    builder.method(method, BodyPublishers.ofByteArray(body))
    headers.foreach { case (k, v) =>
      builder.header(k, v)
    }
    client.future(builder.build())
  }

  def handleRequest(input: InputStream, output: OutputStream, context: Context): Unit = {
    try {
      val req = Json.fromJson[CFRequest](Json.parse(input)).get
      println(s"got request: $req")

      val f = handleRegisterRequest(req)
      .map { res =>
        Json.toJson(res).toString()
      }.flatMap { json =>
        runRequest("PUT", req.ResponseURL, json.getBytes(StandardCharsets.UTF_8), Map("Content-Type" -> ""))
      }
      Await.result(f, 15.minutes)
      output.flush()
      output.close()

    }
    catch {
      case ex: Throwable => ex.printStackTrace()
    }
  }

  def handleRegisterRequest(req: CFRequest): Future[CFResponse] = {
    val responseFut = req.RequestType match {
      case "Create" => handleCreate(req)
      case "Update" => handleUpdate(req)
      case _ => handleDelete(req)
    }
    responseFut.recover {
      case ex: Throwable =>
        val physicalResourceId = req.PhysicalResourceId.getOrElse(UUID.randomUUID().toString)
        failedResponse(ex.getMessage, physicalResourceId, req)
    }
  }

  private def handleDelete(req: CFRequest): Future[CFResponse] = {
    deleteByPhysicalResourceId(req.PhysicalResourceId.get).map { _ =>
      CFResponse("SUCCESS", None, req.PhysicalResourceId.get, req.StackId, req.RequestId, req.LogicalResourceId, Map.empty)
    }
  }

  private def handleUpdate(req: CFRequest): Future[CFResponse] = {
    deleteByPhysicalResourceId(req.PhysicalResourceId.get).flatMap { _ =>
      handleCreate(req)
    }
  }

  private def deleteByPhysicalResourceId(physicalResourceId: String): Future[Any] = {
    cfRegisterDAO.fetchSubscriptionId(physicalResourceId).flatMap {
      case Some(rs) => subscriptionDAO.delete(rs.exchange, rs.subscriptionId)
      case _ => Future.successful(true)
    }.flatMap { _ =>
      cfRegisterDAO.deleteRegistration(physicalResourceId)
    }
  }

  private def handleCreate(req: CFRequest): Future[CFResponse] = {
    val physicalResourceId = req.PhysicalResourceId.getOrElse(UUID.randomUUID().toString)
    req.subscription(accountId) match {
      case Some(sub) =>
        subscriptionDAO.save(sub).flatMap { _ =>
          cfRegisterDAO.saveRegistration(physicalResourceId, sub.id, sub.exchange)
        }.map { _ =>
          CFResponse("SUCCESS", None, physicalResourceId, req.StackId, req.RequestId, req.LogicalResourceId, Map.empty)
        }
      case _ => Future.successful(
        failedResponse(
          s"please submit a valid ${if(req.PhysicalResourceId.isDefined) "update" else "create"} request",
          physicalResourceId,
          req
        )
      )
    }
  }

  private def failedResponse(message: String, physicalResourceId: String, req: CFRequest): CFResponse = {
    CFResponse(
      "FAILED",
      Some(message),
      physicalResourceId,
      req.StackId,
      req.RequestId,
      req.LogicalResourceId,
      Map.empty
    )
  }

}


class RegisterHandlerImpl extends RegisterHandler with AppInit