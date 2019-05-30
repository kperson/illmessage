package com.github.kperson.app

import com.typesafe.config.{Config, ConfigFactory}


class AppConfig(config: Config = ConfigFactory.load().getConfig("app")) {

  def awsRegion: String = config.getString("aws.region")

  def walTable: String = config.getString("aws.wal-table")

  def deliveryTable: String = config.getString("aws.mailbox-table")

  def accountId: String = config.getString("aws.account-id")

  def cfRegistrationTable = config.getString("aws.cf-registration-table")

  def subscriptionTable: String = config.getString("aws.subscription-table")

  def subscriptionMessageSequenceTable: String = config.getString("aws.subscription-message-sequence-table")

  def port: Int = config.getString("port").toInt

}