package com.github.kperson.wal

import com.github.kperson.model.Message


import scala.concurrent.Future


trait WriteAheadDAO {

  def write(messages: List[Message]): Future[List[String]]

  def remove(messageId: String, partitionKey: String): Future[Any]

}
