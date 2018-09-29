package com.github.kperson.aws.s3

import java.text.SimpleDateFormat
import java.util.{Date, TimeZone}

import com.github.kperson.aws.{AWSHttpResponse, HttpRequest}

import scala.concurrent.{ExecutionContext, Future}
import scala.xml.XML

sealed trait StorageClass
case object Standard extends StorageClass
case object StandardInfrequentAccess extends StorageClass
case object ReducedRedundancy extends StorageClass
case object Glacier extends StorageClass
case class UnknownStorageClass(name: String) extends StorageClass

case class DirectoryEntry(key: String, size: Long, storageClass: StorageClass, lastModifiedAt: Date)
case class DirectoryListing(nextContinuationToken: Option[String], entries: List[DirectoryEntry])


trait ObjectOps {

  def request(
    method: String,
    path: String,
    payload: Array[Byte] = Array.empty,
    headers: Map[String, String] = Map.empty,
    queryParams: Map[String, Option[String]] = Map.empty
  ): HttpRequest


  val listDateFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.S'Z'")
  listDateFormatter.setTimeZone(TimeZone.getTimeZone("GMT"))

  /**
    * Puts a object into a bucket
    *
    * @param bucket the name of the bucket
    * @param key the name of the key
    * @param value the value of the object in bytes
    * @param contentType the content type of the data (defaults to application/octet-stream)
    * @param storageClass the S3 storage class
    * @return a future of a async response
    */
  def put(
   bucket: String,
   key: String,
   value: Array[Byte],
   contentType: String = "application/octet-stream",
   storageClass: StorageClass = Standard
  ): Future[AWSHttpResponse] = {
    val path =  s"/$bucket" + (if(key.startsWith("/")) key else "/" + key)

    val storageClassHeaderValue = storageClass match {
      case Standard => "STANDARD"
      case StandardInfrequentAccess => "STANDARD_IA"
      case ReducedRedundancy => "REDUCED_REDUNDANCY"
      case Glacier => throw new RuntimeException("you may not set a storage class of GLACIER for a PUT")
      case UnknownStorageClass(_) => throw new RuntimeException("unknown storage class allows future storage class compatibility, only use STANDARD, STANDARD_IA, and REDUCED_REDUNDANCY")
    }

    val requestHeaders = Map(
      "Content-Type" -> contentType,
      "x-amz-storage-class" -> storageClassHeaderValue
    )
    request("PUT", path, headers = requestHeaders).run()
  }

  /**
    * Deletes a object from a bucket
    *
    * @param bucket the name of the bucket
    * @param key the name of the key
    * @return a future of a async response
    */
  def delete(bucket: String, key: String): Future[AWSHttpResponse] = {
    val path = s"/$bucket" + (if(key.startsWith("/")) key else "/" + key)
    request("DELETE", path).run()
  }

  /**
    * Gets a object
    *
    * @param bucket the name of the bucket
    * @param key the name of the key
    * @return a future of a async response
    */
  def get(bucket: String, key: String): Future[AWSHttpResponse] = {
    val path = s"/$bucket" + (if(key.startsWith("/")) key else "/" + key)
    request("GET", path).run()
  }

  /**
    * List the keys within a bucket
    *
    * @param bucket the name of the bucket
    * @param prefix a prefix to limit the listing
    * @param maxKeys the max number of results to return
    * @param continuationToken a pagination token to continue fetching from end of past list request
    * @param ec the execution context for future transformation
    * @return a future of a directory listing
    */
  def list(
    bucket: String,
    prefix: Option[String] = None,
    maxKeys: Option[Int] = None,
    continuationToken: Option[String] = None
  )(implicit ec: ExecutionContext): Future[DirectoryListing] = {
    val path = s"/$bucket"

    var m = scala.collection.mutable.Map[String, Option[String]]()
    prefix.foreach { p => m = m ++ Map("prefix" -> Some(p)) }
    maxKeys.foreach { p => m = m ++ Map("max-keys" -> Some(p.toString)) }
    continuationToken.foreach { p => m = m ++ Map("continuation-token" -> Some(p)) }

    val params = Map("list-type" -> Some("2")) ++ m
    val f = request("GET", path, queryParams = params).run()
    f.map { resp =>
      val xml = XML.loadString(new String(resp.body))
      val isTruncated = (xml \ "IsTruncated").text != "false"
      val nextContinuationToken = if(isTruncated) Some((xml \ "NextContinuationToken").text) else None
      val entries = (xml  \\ "ListBucketResult" \\ "Contents").map { contents =>
        val key = (contents \ "Key").text
        val size = (contents \ "Size").text.toLong
        val storageClassStr = (contents \ "StorageClass").text
        val storageClass = storageClassStr match {
          case "STANDARD" => Standard
          case "STANDARD_IA" => StandardInfrequentAccess
          case "REDUCED_REDUNDANCY" => ReducedRedundancy
          case "GLACIER" => Glacier
          case x => UnknownStorageClass(x)
        }
        DirectoryEntry(key, size, storageClass, listDateFormatter.parse((contents \ "LastModified").text))
      }.toList

      DirectoryListing(nextContinuationToken, entries)
    }
  }

}
