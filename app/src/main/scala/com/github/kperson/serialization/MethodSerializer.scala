package com.github.kperson.serialization

import com.github.kperson.lambda._
import play.api.libs.json._


trait MethodSerializer {

  implicit val methodWrites: Writes[Method] = {
    case GET => JsString("GET")
    case POST => JsString("POST")
    case PUT => JsString("PUT")
    case DELETE => JsString("DELETE")
    case PATCH => JsString("PATCH")
    case OPTIONS => JsString("OPTIONS")
    case TRACE => JsString("TRACE")
    case CONNECT => JsString("CONNECT")
    case HEAD => JsString("HEAD")
  }

  implicit val methodReads: Reads[Method] = { value =>
    value.asInstanceOf[JsString].value match {
      case "GET" =>  JsSuccess(GET)
      case "POST" => JsSuccess(POST)
      case "PUT" => JsSuccess(PUT)
      case "DELETE" => JsSuccess(DELETE)
      case "PATCH" => JsSuccess(PATCH)
      case "OPTIONS" => JsSuccess(OPTIONS)
      case "TRACE" => JsSuccess(TRACE)
      case "CONNECT" => JsSuccess(CONNECT)
      case "HEAD" => JsSuccess(HEAD)
    }
  }

}


