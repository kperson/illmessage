package com.github.kperson.test.http


import org.json4s.{Formats, NoTypeHints}
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization._

import scala.io.Source
import scala.reflect.Manifest


case class MockRequest(body: AnyRef)

object HttpFixtures {

  implicit val formats: Formats = Serialization.formats(NoTypeHints)

  def request(file: String, key: String): String = {
    val json = Source.fromURL(getClass.getResource(s"/fixtures/$file"))
    val requests = read[Map[String, MockRequest]](json.mkString)
    write(requests(key).body)
  }

  def jsonMatches[A](toMatchJSON: String, expectedJSON: String)(implicit mf: Manifest[A]): Boolean = {
    read[A](toMatchJSON) == read[A](expectedJSON)
  }


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
