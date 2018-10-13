package com.github.kperson.app




object Main extends App {

  val config = new AppConfig()

//  implicit val system: ActorSystem = ActorSystem("app")
//  implicit val materializer: Materializer = ActorMaterializer()
//  implicit val ec: ExecutionContext = materializer.executionContext
//
//  try {
//    val dynamoClient = new DynamoClient(config.awsRegion)
//    val wal = new WAL(dynamoClient, config.walTable)
//    val walTransfer = new WALTransfer(wal)
//    val s3Client = new S3Client(config.awsRegion)
//    val subscriptionDAO = new AmazonSubscriptionDAO(config.awsBucket, s3Client)
//    val sqsClient = new SQSClient(config.awsRegion, "N/A")
//    val deadLetter = new DeadLetterQueue(dynamoClient, config.deadLetterTable, wal)
//
//    val (orderingFlow, onMessageSent) = Multiplex.flow(new MessageACK(wal))
//
//    val stream = MessageSubscriptionSource(subscriptionDAO, walTransfer)
//      .via(orderingFlow)
//      .via(MessageDelivery(sqsClient, deadLetter))
//      .runForeach { xs =>
//        xs.foreach { x =>
//          onMessageSent(x.subscription.id, x.messageId)
//        }
//      }
//
//    val httpAdapter = new HttpAdapter(walTransfer, subscriptionDAO)
//    httpAdapter.run(port = config.port)
//
//    stream.failed.foreach { ex =>
//      ex.printStackTrace()
//      sys.exit(1)
//    }
//
//  }
//  catch {
//    case ex: Throwable =>
//      ex.printStackTrace()
//      sys.exit(1)
//  }

}