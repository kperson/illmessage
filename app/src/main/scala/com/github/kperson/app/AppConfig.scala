package com.github.kperson.app


class AppConfig() {

  def awsRegion: String = System.getenv("REGION")

  def walTable: String = System.getenv("WAL_TABLE")

  def deliveryTable: String = System.getenv("MAILBOX_TABLE")

  def accountId: String = System.getenv("ACCOUNT_ID")

  def cfRegistrationTable: String = System.getenv("CF_REGISTRATION_TABLE")

  def subscriptionTable: String = System.getenv("SUBSCRIPTION_TABLE")

  def subscriptionMessageSequenceTable: String = System.getenv("SUBSCRIPTION_MESSAGE_TABLE")

  def port: Int = if(System.getenv().containsKey("PORT")) System.getenv("PORT").toInt else 8080

  def apiEndpoint: String = System.getenv("API_ENDPOINT")

}