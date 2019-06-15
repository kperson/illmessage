package com.github.kperson.aws.dynamo

import java.util.Base64


import play.api.libs.json._


sealed trait DynamoPrimitive {

  def flatten: Any
  def asDynamo: Map[String, Any]

}

object DynamoSerialization {

  implicit class RichJObject(self: JsValue) {
    def rawToDynamo: DynamoPrimitive = {
      self match {
        case JsBoolean(bool) => DynamoBoolean(bool)
        case JsNumber(value) => DynamoBigDecimal(value)
        case JsString(str) => DynamoString(str)
        case JsNull => DynamoNull
        case JsObject(l) =>
          val m = l.map { case (k, v) =>
            (k, v.rawToDynamo)
          }
          DynamoMap(m.toMap)
        case JsArray(arr) =>
          DynamoList(arr.map { _.rawToDynamo }.toList)
        case  _ => DynamoNull
      }
    }
  }

}

object DynamoPrimitive {

  def fromJValue: PartialFunction[JsValue, DynamoPrimitive] = {
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

  def fromAny: PartialFunction[Any, JsValue] = {
    case x: String => JsString(x)
    case x: Int => JsNumber(BigDecimal(x))
    case x: Long => JsNumber(BigDecimal(x))
    case x: Double => JsNumber(BigDecimal(x))
    case x: BigDecimal => JsNumber(x)
    case x: Boolean => JsBoolean(x)
    case x: Array[Any] => JsArray(x.map(fromAny))
    case x: Map[_, _] => JsObject(x.map { case (k, v) => (k.asInstanceOf[String], fromAny(v)) })
    case x: Seq[Any] => JsArray(x.map(fromAny))
    case _ => JsNull
  }

}

case class DynamoString(value: String) extends DynamoPrimitive {

  def flatten: String = value

  def asDynamo: Map[String, String] = {
    Map("S" -> value)
  }

}

object DynamoString {

  def unapply(arg: JsValue): Option[String] = {
    arg match {
      case JsObject(m) => m.get("S").map { _.asInstanceOf[JsString].value }
      case _ => None
    }
  }

}


object DynamoNull extends DynamoPrimitive {

  def unapply(arg: JsValue): Boolean = {
    arg match {
      case JsObject(m) if m.contains("NULL") => m("NULL").asInstanceOf[JsBoolean].value
      case _ => false
    }
  }

  def flatten: Any = null

  def asDynamo: Map[String, Boolean] = {
    Map("NULL" -> true)
  }

}

case class DynamoBinary(value: Array[Byte]) extends DynamoPrimitive with Equals {

  def canEqual(that: Any): Boolean = that.isInstanceOf[DynamoBinary]

  override def equals(obj: scala.Any): Boolean = {
    canEqual(obj) && obj.asInstanceOf[DynamoBinary].value.deep == value.deep
  }

  def flatten: String = Base64.getEncoder.encodeToString(value)

  def asDynamo: Map[String, String] = {
    Map("B" -> flatten)
  }

}
object DynamoBinary {

  def unapply(arg: JsValue): Option[Array[Byte]] = {
    arg match {
      case JsObject(m) if m.contains("B") => Some(Base64.getDecoder.decode(m("B").asInstanceOf[JsString].value))
      case _ => None
    }
  }

}


case class DynamoBigDecimal(value: BigDecimal) extends DynamoPrimitive {

  def flatten: BigDecimal = value

  def asDynamo: Map[String, String] = {
    Map("N" -> flatten.toString())
  }


}

object DynamoBigDecimal {

  def unapply(arg: JsValue): Option[BigDecimal] = {
    arg match {
      case JsObject(m) if m.contains("N") => Some(BigDecimal(m("N").asInstanceOf[JsString].value))
      case _ => None
    }
  }


}

case class DynamoBoolean(value: Boolean) extends DynamoPrimitive {

  def flatten: Boolean = value

  def asDynamo: Map[String, Boolean] = {
    Map("BOOL" -> value)
  }


}

object DynamoBoolean {

  def unapply(arg: JsValue): Option[Boolean] = {
    arg match {
      case JsObject(m) if m.contains("BOOL") => Some(m("BOOL").asInstanceOf[JsBoolean].value)
      case _ => None
    }
  }


}

case class DynamoStringSet(value: Set[String]) extends DynamoPrimitive {

  def flatten: Set[String] = value

  def asDynamo: Map[String, Set[String]] = {
    Map("SS" -> value)
  }


}

object DynamoStringSet {

  def unapply(arg: JsValue): Option[Set[String]] = {
    arg match {
      case JsObject(m) if m.contains("SS") =>
        Some(m("SS").as[JsArray].value.map { _.asInstanceOf[JsString].value }.toSet)
      case _ => None
    }
  }


}

case class DynamoBigDecimalSet(value: Set[BigDecimal]) extends DynamoPrimitive {

  def flatten: Set[BigDecimal] = value

  def asDynamo: Map[String, Set[String]] = {
    Map("NS" -> value.map { _.toString() })
  }

}

object DynamoBigDecimalSet {
  def unapply(arg: JsValue): Option[Set[BigDecimal]] = {
    arg match {
      case JsObject(m) if m.contains("NS") =>
        Some(m("NS").as[JsArray].value.map { _.asInstanceOf[JsNumber].value }.toSet)
      case _ => None
    }
  }

}


case class DynamoBinarySet(value: Set[Array[Byte]]) extends DynamoPrimitive with Equals {

  def canEqual(that: Any): Boolean = that.isInstanceOf[DynamoBinarySet]

  override def equals(obj: scala.Any): Boolean = {
    canEqual(obj) && obj.asInstanceOf[DynamoBinarySet].value.map { _.deep } == value.map { _.deep }
  }

  def flatten: Set[String] = value.map { Base64.getEncoder.encodeToString(_) }

  def asDynamo: Map[String, Set[String]] = {
    Map("BS" -> flatten)
  }

}

object DynamoBinarySet {

  def unapply(arg: JsValue): Option[Set[Array[Byte]]] = {
    arg match {
      case JsObject(m) if m.contains("BS") =>
        Some(m("BS").as[JsArray].value.map { x => Base64.getDecoder.decode(x.asInstanceOf[JsString].value ) }.toSet)
      case _ => None
    }
  }



}


case class DynamoList(value: List[DynamoPrimitive]) extends DynamoPrimitive {

  def flatten: List[Any] = value.map { _.flatten }

  def asDynamo: Map[String, List[Any]] = {
    Map("L" -> value.map { _.asDynamo })
  }

}

object DynamoList {

  def unapply(arg: JsValue): Option[List[DynamoPrimitive]] = {
    arg match {
      case JsObject(m) if m.contains("L") =>
        Some(m("L").asInstanceOf[JsArray].value.collect(DynamoPrimitive.fromJValue).toList)
      case _ => None
    }
  }


}


case class DynamoMap(value: Map[String, DynamoPrimitive]) extends DynamoPrimitive {

  def flatten: Map[String, Any] = value.map { case (k, v) => (k, v.flatten) }

  def asDynamo: Map[String, Map[String, Any]] = {
    val x = value.map { case (k, v) => (k, v.asDynamo) }
    Map("M" -> x)
  }

}

object DynamoMap {

  def unapply(arg: Map[String, DynamoPrimitive]) = Some(arg)

  def unapply(arg: JsValue): Option[Map[String, DynamoPrimitive]] = {
    arg match {
      case JsObject(m) if m.contains("M") =>
        val myMap = m("M").asInstanceOf[JsObject]
        val res = myMap.value.map { case (k, v) => (k, DynamoPrimitive.fromJValue(v)) }.toMap
        Some(res)
      case _ => None
    }
  }

}