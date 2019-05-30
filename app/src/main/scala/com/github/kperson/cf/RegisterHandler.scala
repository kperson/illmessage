package com.github.kperson.cf

import java.io.{InputStream, OutputStream}
import java.util.UUID

import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}

import com.github.kperson.app.AppInit
import com.github.kperson.aws.AWSHttp._
import com.github.kperson.subscription.SubscriptionDAO

import org.asynchttpclient.{Dsl, RequestBuilder}
import org.json4s.jackson.Serialization
import org.json4s.{Formats, NoTypeHints}

import scala.concurrent.{ExecutionContext, Future}


trait RegisterHandler extends RequestStreamHandler {

  implicit val formats: Formats = Serialization.formats(NoTypeHints)
  implicit val ec: ExecutionContext

  def accountId: String
  def subscriptionDAO: SubscriptionDAO
  def cfRegisterDAO: CFRegisterDAO


  def handleRequest(input: InputStream, output: OutputStream, context: Context): Unit = {
    val req = Serialization.read[CFRequest](input)
    handleRegisterRequest(req)
      .map { res =>
        Serialization.write(res)
      }.map { json =>
      val builder = new RequestBuilder("POST", true)
      builder.setUrl(req.ResponseURL)
      builder.setBody(json)
      builder.setHeader("Content-Type", "application/json")
      Dsl.asyncHttpClient().requestFuture(builder.build())
    }.foreach { _ =>
      output.flush()
      output.close()
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