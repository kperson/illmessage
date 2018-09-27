package com.github.kperson.aws

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.{Date, TimeZone}
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import com.amazonaws.auth.{AWSCredentials, BasicSessionCredentials}
import com.amazonaws.util.SdkHttpUtils

sealed trait S3Payload


//https://docs.aws.amazon.com/AmazonS3/latest/dev/RESTAuthentication.html

case class AWSSigning(
  service: String,
  credentials: AWSCredentials,
  region: String,
  httpMethod: String,
  canonicalURI: String,
  headersToSign: Map[String, String],
  queryParams: Map[String, Option[String]] = Map.empty,
  payload: Array[Byte] = Array.empty,
  date: Date = new Date()
) {

  val payloadHash: String = AWSSigning.toHex(AWSSigning.SHA256(payload))
  val encodeURI: String = AWSSigning.uriEncode(canonicalURI, isParameter = false)

  val securityTokenHeaders: Map[String, String] =
    credentials match {
      case c: BasicSessionCredentials => Map("x-amz-security-token" -> c.getSessionToken)
      case _ => Map.empty
    }

  val headersWithoutSignature: Map[String, String] = headersToSign ++ Map(
    "x-amz-content-sha256" -> payloadHash,
    "x-amz-date" -> AWSSigning.longDateFormatter.format(date)
  ) ++ securityTokenHeaders

  def sortedHeaderKeys: List[String] = headersWithoutSignature.keys.toList.sortWith { (kOne, kTwo) => kOne.toLowerCase < kTwo.toLowerCase }

  def encodedParams: Map[String, String] = {
    queryParams.map { case (k, v) =>
      (AWSSigning.uriEncode(k, isParameter = true), AWSSigning.uriEncode(v.getOrElse(""), isParameter = true))
    }
  }

  def sortedParams: List[(String, String)] = {
    val ep = encodedParams
    val sortedKeys = ep.keys.toList.sortWith { (kOne, kTwo) => kOne < kTwo }
    sortedKeys.map { k =>
      (k, ep(k))
    }
  }

  def canonicalRequest: String = {
    val strBuilder = new StringBuilder()
    strBuilder.append(httpMethod + "\n")
    strBuilder.append(encodeURI + "\n")
    strBuilder.append(sortedParams.map { case (k, v) => s"$k=$v" }.mkString("&") + "\n")

    val sortedKeys = sortedHeaderKeys
    sortedKeys.foreach { h =>
      strBuilder.append(s"${h.toLowerCase}:${headersWithoutSignature(h)}\n")
    }
    strBuilder.append("\n")

    strBuilder.append(sortedKeys.map{ _.toLowerCase }.mkString(";") + "\n")
    strBuilder.append(payloadHash)

    strBuilder.toString
  }

  def stringToSign: String = {
    val cr = canonicalRequest
    val crHash = AWSSigning.SHA256(cr.getBytes(StandardCharsets.UTF_8))
    s"${AWSSigning.signingVersion}-${AWSSigning.signingAlgorithm}" +
      "\n" + AWSSigning.longDateFormatter.format(date) +
      "\n" + AWSSigning.shortDateFormatter.format(date) + "/" + region + s"/$service/${AWSSigning.signingVersion.toLowerCase}_request" +
      "\n" + AWSSigning.toHex(crHash)

  }

  def signature: String = {
    val kSecret = (AWSSigning.signingVersion + credentials.getAWSSecretKey).getBytes(StandardCharsets.UTF_8)
    val kDate = AWSSigning.hmacSHA256(AWSSigning.shortDateFormatter.format(date), kSecret)
    val kRegion = AWSSigning.hmacSHA256(region, kDate)
    val kService = AWSSigning.hmacSHA256(service, kRegion)
    val kSigning = AWSSigning.hmacSHA256(s"${AWSSigning.signingVersion.toLowerCase}_request", kService)
    AWSSigning.toHex(AWSSigning.hmacSHA256(stringToSign, kSigning))
  }

  def authorizationHeader: String = {
      val sortedHeadersDelimited = sortedHeaderKeys.map { _.toLowerCase }.mkString(";")
      s"${AWSSigning.signingVersion}-${AWSSigning.signingAlgorithm} Credential=${credentials.getAWSAccessKeyId}/${AWSSigning.shortDateFormatter.format(date)}/$region/$service/${AWSSigning.signingVersion.toLowerCase}_request,SignedHeaders=$sortedHeadersDelimited,Signature=$signature"

  }

  def headers: Map[String, String] = {
    headersWithoutSignature ++ Map("Authorization" -> authorizationHeader)
  }

}

object AWSSigning {

  val signingVersion = "AWS4"
  val signingAlgorithm = "HMAC-SHA256"

  private lazy val shortDateFormatter = sFormatter()
  private lazy val longDateFormatter = lFormatter()

  private def sFormatter(): SimpleDateFormat = {
    val f = new SimpleDateFormat("yyyyMMdd")
    f.setTimeZone(TimeZone.getTimeZone("GMT"))
    f
  }

  private def lFormatter(): SimpleDateFormat = {
    val f = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'")
    f.setTimeZone(TimeZone.getTimeZone("GMT"))
    f
  }

  def hmacSHA256(data: String, key: Array[Byte]): Array[Byte] = {
    val algorithm = "HmacSHA256"
    val mac = Mac.getInstance(algorithm)
    mac.init(new SecretKeySpec(key, algorithm))
    mac.doFinal(data.getBytes(StandardCharsets.UTF_8))
  }

  def SHA256(bytes: Array[Byte]): Array[Byte] = {
    val sha256Digest = MessageDigest.getInstance("SHA-256")
    sha256Digest.digest(bytes)
  }

  def toHex(bytes: Array[Byte]): String = bytes.map("%02x".format(_)).mkString

  def uriEncode(input: String, isParameter: Boolean): String = {
    SdkHttpUtils.urlEncode(input, !isParameter)
  }

}