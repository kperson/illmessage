package com.github.kperson.app

import com.typesafe.config.{Config, ConfigFactory}


class AppConfig(config: Config = ConfigFactory.load().getConfig("app")) {

  def awsRegion: String = config.getString("aws.region")

  def walTable: String = config.getString("aws.wal-table")

  def subscriptionTable: String = config.getString("aws.subscription-table")

  def deadLetterTable: String = config.getString("aws.dead-letter-table")

  def port: Int = config.getString("port").toInt

}