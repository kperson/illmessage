package com.github.kperson.serialization

import com.github.kperson.lambda._

import org.json4s.CustomSerializer
import org.json4s.JsonAST.JString

class MethodSerializer extends CustomSerializer[Method](_ => (
  {
    case JString(name) => name match {
      case "GET" => GET
      case "POST" => POST
      case "PUT" => PUT
      case "DELETE" => DELETE
      case "PATCH" => PATCH
      case "OPTIONS" => OPTIONS
      case "TRACE" => TRACE
      case "CONNECT" => CONNECT
      case "HEAD" => HEAD

    }
  },
  {
    case GET => JString("GET")
    case POST => JString("POST")
    case PUT => JString("PUT")
    case DELETE => JString("DELETE")
    case PATCH => JString("PATCH")
    case OPTIONS => JString("OPTIONS")
    case TRACE => JString("TRACE")
    case CONNECT => JString("CONNECT")
    case HEAD => JString("HEAD")
  })
)