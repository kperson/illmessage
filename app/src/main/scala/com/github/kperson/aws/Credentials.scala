package com.github.kperson.aws

import com.amazonaws.auth.{AWSCredentials, DefaultAWSCredentialsProviderChain}

object Credentials {

  def defaultCredentials: AWSCredentials = {
    //https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/credentials.html
    val providerChain = DefaultAWSCredentialsProviderChain.getInstance()
    providerChain.setReuseLastProvider(true)
    providerChain.getCredentials
  }

}