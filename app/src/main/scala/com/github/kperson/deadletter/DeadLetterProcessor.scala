package com.github.kperson.deadletter

import java.io.{InputStream, OutputStream}

import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}

import scala.io.Source

trait DeadLetterProcessor extends RequestStreamHandler {

  def handleRequest(input: InputStream, output: OutputStream, context: Context) {
    println(Source.fromInputStream(input).mkString)
    output.write("HELLO WORLD".getBytes)
    output.close()
  }

}

class DeadLetterProcessorImpl extends DeadLetterProcessor
