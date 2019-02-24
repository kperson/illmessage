package com.github.kperson.test.http

import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model.HttpEntity.Empty
import akka.http.scaladsl.model.{HttpEntity, HttpMethod, HttpMethods, HttpRequest}
import org.json4s.JsonAST.JString
import org.json4s.{CustomSerializer, Formats, NoTypeHints}
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization._
import org.scalatest.Matchers

import scala.io.Source
import scala.reflect.Manifest

case class MockRequest(path: String, method: HttpMethod, body: Option[AnyRef])


class AkkaMethodSerializer extends CustomSerializer[HttpMethod](_ => (
  {
    case JString(name) => name match {
      case "GET" => HttpMethods.GET
      case "POST" => HttpMethods.POST
      case "PUT" => HttpMethods.PUT
      case "DELETE" => HttpMethods.DELETE
      case "PATCH" => HttpMethods.PATCH
      case "OPTIONS" => HttpMethods.OPTIONS
      case "TRACE" => HttpMethods.TRACE
      case "CONNECT" => HttpMethods.CONNECT
      case "HEAD" => HttpMethods.HEAD

    }
  },
  {
    case HttpMethods.GET => JString("GET")
    case HttpMethods.POST => JString("POST")
    case HttpMethods.PUT => JString("PUT")
    case HttpMethods.DELETE => JString("DELETE")
    case HttpMethods.PATCH => JString("PATCH")
    case HttpMethods.OPTIONS => JString("OPTIONS")
    case HttpMethods.TRACE => JString("TRACE")
    case HttpMethods.CONNECT => JString("CONNECT")
    case HttpMethods.HEAD => JString("HEAD")
  })
)

object HttpFixturesFixtures extends RequestBuilding {

  implicit val formats: Formats = Serialization.formats(NoTypeHints) ++ (
    new AkkaMethodSerializer() ::
    Nil
  )

  def request(file: String, key: String): HttpRequest = {
    val json = Source.fromURL(getClass.getResource(s"/fixtures/$file"))

    val requests = read[Map[String, MockRequest]](json.mkString)
    val mockRequest = requests(key)
    val body = mockRequest.body
      .map { b => HttpEntity(write(b)) }
      .getOrElse(HttpEntity.empty(Empty.contentType))

    new RequestBuilder(mockRequest.method)(mockRequest.path, body)
  }

  def jsonMatches[A](toMatchJSON: String, expectedJSON: String)(implicit mf: Manifest[A]): Boolean =
    read[A](toMatchJSON) == read[A](expectedJSON)


  def response[A](file: String, key: String)(implicit mf: Manifest[A]): String = {
    val json = Source.fromURL(getClass.getResource(s"/fixtures/$file"))
    val responses = read[Map[String, AnyRef]](json.mkString)
    write(responses(key))
  }

  def jsonMatchesResponse[A](
    responseJSON: String,
    file: String,
    key: String
  )(implicit mf: Manifest[A]): Boolean = {
    jsonMatches(responseJSON, response[A](file, key))
  }

}
