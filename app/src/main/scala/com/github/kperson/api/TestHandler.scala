package com.github.kperson.api

import java.io.{InputStream, OutputStream}
import java.nio.charset.StandardCharsets

import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}

class TestHandler extends RequestStreamHandler {

  override def handleRequest(input: InputStream, output: OutputStream, context: Context) {
    println(System.getenv())
    val in = scala.io.Source.fromInputStream(input).mkString
    output.write(s"echo: $in".getBytes(StandardCharsets.UTF_8))
    output.flush()
    output.close()
  }

}
