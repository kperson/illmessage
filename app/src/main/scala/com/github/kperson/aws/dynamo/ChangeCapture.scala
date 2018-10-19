package com.github.kperson.aws.dynamo

sealed trait ChangeCapture[T] {

  def map[Q](func: T => Q): ChangeCapture[Q]

  def eventType: String = {
    this match {
      case New(_, _) => "NEW"
      case Delete(_, _) => "DELETE"
      case Update(_, _, _) => "UPDATE"
    }
  }

}


case class New[T](
  eventSource: String,
  item: T
) extends ChangeCapture[T] {

  def map[Q](func: T => Q) = New(eventSource, func(item))

}

case class Delete[T](
  eventSource: String,
  item: T
) extends ChangeCapture[T] {

  def map[Q](func: T => Q) = Delete(eventSource, func(item))

}


case class Update[T](
  eventSource: String,
  oldItem: T,
  newItem: T
) extends ChangeCapture[T] {

  def map[Q](func: T => Q) = Update(eventSource, func(oldItem), func(newItem))

}