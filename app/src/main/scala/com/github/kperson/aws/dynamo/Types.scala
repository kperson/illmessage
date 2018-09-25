package com.github.kperson.aws.dynamo

import java.util.Base64

import org.json4s.JsonAST._

import scala.reflect.runtime.universe._

sealed trait DynamoPrimitive {

  def flatten: Any
  def asDynamo: Map[String, Any]

}

object DynamoSerialization {

  implicit class RichJObject(self: JValue) {
    def rawToDynamo: Option[DynamoPrimitive] = {
      self match {
        case JBool(bool) => Some(DynamoBoolean(bool))
        case JInt(int) => Some(DynamoBigDecimal(BigDecimal(int)))
        case JLong(long) => Some(DynamoBigDecimal(BigDecimal(long)))
        case JDouble(double) => Some(DynamoBigDecimal(BigDecimal(double)))
        case JDecimal(decimal) => Some(DynamoBigDecimal(decimal))
        case JString(str) => Some(DynamoString(str))
        case JNull => Some(DynamoNull)
        case JObject(l) =>
          val m = l.flatMap { case (k, v) =>
            v.rawToDynamo match {
              case Some(a) => Some((k, a))
              case _ => None
            }
          }.toMap
          Some(DynamoMap(m))
        case JArray(arr) =>
          Some(DynamoList(arr.flatMap { _.rawToDynamo }))
        case JSet(set) => Some(DynamoList(set.flatMap { _.rawToDynamo }.toList))
        case JNothing => None
      }
    }
  }

}

object DynamoPrimitive {

  def fromJValue: PartialFunction[JValue, DynamoPrimitive] = {
    case DynamoString(str) => DynamoString(str)
    case DynamoBinary(arr) => DynamoBinary(arr)
    case DynamoBigDecimal(dec) => DynamoBigDecimal(dec)
    case DynamoBoolean(bool) => DynamoBoolean(bool)
    case DynamoNull() =>  DynamoNull
    case DynamoStringSet(set) => DynamoStringSet(set)
    case DynamoBigDecimalSet(set) => DynamoBigDecimalSet(set)
    case DynamoBinarySet(set) => DynamoBinarySet(set)
    case DynamoList(list) => DynamoList(list)
    case DynamoMap(map) => DynamoMap(map)
  }


}

case class DynamoString(value: String) extends DynamoPrimitive {

  def flatten = value

  def asDynamo = {
    Map("S" -> value)
  }

}

object DynamoString {

  def unapply(arg: JValue): Option[String] = {
    arg match {
      case JObject(List(("S", JString(str)))) => Some(str)
      case _ => None
    }
  }

}


object DynamoNull extends DynamoPrimitive {

  def unapply(arg: JValue): Boolean = {
    arg match {
      case JObject(List(("NULL", JBool(bool)))) if bool => true
      case _ => false
    }
  }

  def flatten = null

  def asDynamo = {
    Map("NULL" -> true)
  }

}

case class DynamoBinary(value: Array[Byte]) extends DynamoPrimitive with Equals {

  def canEqual(that: Any): Boolean = that.isInstanceOf[DynamoBinary]

  override def equals(obj: scala.Any): Boolean = {
    canEqual(obj) && obj.asInstanceOf[DynamoBinary].value.deep == value.deep
  }

  def flatten = Base64.getEncoder.encodeToString(value)

  def asDynamo = {
    Map("B" -> flatten)
  }

}
object DynamoBinary {

  def unapply(arg: JValue): Option[Array[Byte]] = {
    arg match {
      case JObject(List(("B", JString(str)))) => Some(Base64.getDecoder.decode(str))
      case _ => None
    }
  }

}


case class DynamoBigDecimal(value: BigDecimal) extends DynamoPrimitive {

  def flatten = value

  def asDynamo = {
    Map("N" -> flatten.toString())
  }


}

object DynamoBigDecimal {

  def unapply(arg: JValue): Option[BigDecimal] = {
    arg match {
      case JObject(List(("N", JString(str)))) => Some(BigDecimal(str))
      case _ => None
    }
  }


}

case class DynamoBoolean(value: Boolean) extends DynamoPrimitive {

  def flatten = value

  def asDynamo = {
    Map("BOOL" -> value)
  }


}

object DynamoBoolean {

  def unapply(arg: JValue): Option[Boolean] = {
    arg match {
      case JObject(List(("BOOL", JBool(bool)))) => Some(bool)
      case _ => None
    }
  }


}

case class DynamoStringSet(value: Set[String]) extends DynamoPrimitive {

  def flatten = value

  def asDynamo = {
    Map("SS" -> value)
  }


}

object DynamoStringSet {

  def unapply(arg: JValue): Option[Set[String]] = {
    arg match {
      case JObject(List(("SS", JArray(arr)))) =>
        Some(arr.map { _.asInstanceOf[JString].s }.toSet)
      case _ => None
    }
  }


}

case class DynamoBigDecimalSet(value: Set[BigDecimal]) extends DynamoPrimitive {

  def flatten = value

  def asDynamo = {
    Map("NS" -> value.map { _.toString() })
  }

}

object DynamoBigDecimalSet {

  def unapply(arg: JValue): Option[Set[BigDecimal]] = {
    arg match {
      case JObject(List(("NS", JArray(arr)))) =>
        Some(arr.map { _.asInstanceOf[JString].s }.map { BigDecimal(_) }.toSet)
      case _ => None
    }
  }

}


case class DynamoBinarySet(value: Set[Array[Byte]]) extends DynamoPrimitive with Equals {

  def canEqual(that: Any): Boolean = that.isInstanceOf[DynamoBinarySet]

  override def equals(obj: scala.Any): Boolean = {
    canEqual(obj) && obj.asInstanceOf[DynamoBinarySet].value.map { _.deep } == value.map { _.deep }
  }

  def flatten = value.map { Base64.getEncoder.encodeToString(_) }

  def asDynamo = {
    Map("BS" -> flatten)
  }

}

object DynamoBinarySet {

  def unapply(arg: JValue): Option[Set[Array[Byte]]] = {
    arg match {
      case JObject(List(("BS", JArray(arr)))) =>
        Some(arr.map { _.asInstanceOf[JString].s }.map { Base64.getDecoder.decode(_) }.toSet)
      case _ => None
    }
  }



}


case class DynamoList(value: List[DynamoPrimitive]) extends DynamoPrimitive {

  def flatten = value.map { _.flatten }

  def asDynamo = {
    Map("L" -> value.map { _.asDynamo })
  }

}

object DynamoList {

  def unapply(arg: JValue): Option[List[DynamoPrimitive]] = {
    arg match {
      case JObject(List(("L", JArray(arr)))) =>
        val l: List[DynamoPrimitive] = arr.collect(DynamoPrimitive.fromJValue)
        Some(l)
      case _ => None
    }
  }


}


case class DynamoMap(value: Map[String, DynamoPrimitive]) extends DynamoPrimitive {

  def flatten: Map[String, Any] = value.map { case (k, v) => (k, v.flatten) }

  def asDynamo = {
    val x = value.map { case (k, v) => (k, v.asDynamo) }
    Map("M" -> x)
  }

}

object DynamoMap {

  def unapply(arg: Map[String, DynamoPrimitive]) = Some(arg)

  def unapply(arg: JValue): Option[Map[String, DynamoPrimitive]] = {
    arg match {
      case JObject(List(("M", JObject(obj)))) =>
        val m = obj.map { case (k, v) =>
          println("HERE")
          println(v)
          (k, DynamoPrimitive.fromJValue(v)) }.toMap
        Some(m)
      case _ => None
    }
  }

}