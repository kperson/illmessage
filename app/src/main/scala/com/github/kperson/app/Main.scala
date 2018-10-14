package com.github.kperson.app

import java.io.{ByteArrayInputStream, FileOutputStream}


object Main extends App {

  import Init._

  api.run(port = config.port)

  val req = """{
    |    "resource": "/{proxy+}",
    |    "path": "/fdfd",
    |    "httpMethod": "GET",
    |    "headers": {
    |        "accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8",
    |        "accept-encoding": "gzip, deflate, br",
    |        "accept-language": "en-US,en;q=0.9",
    |        "cache-control": "max-age=0",
    |        "Host": "t56efl9s9h.execute-api.us-east-1.amazonaws.com",
    |        "upgrade-insecure-requests": "1",
    |        "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36",
    |        "X-Amzn-Trace-Id": "Root=1-5bc38070-75032a18c65115ec87392d7e",
    |        "X-Forwarded-For": "71.239.243.2",
    |        "X-Forwarded-Port": "443",
    |        "X-Forwarded-Proto": "https"
    |    },
    |    "multiValueHeaders": {
    |        "accept": [
    |            "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8"
    |        ],
    |        "accept-encoding": [
    |            "gzip, deflate, br"
    |        ],
    |        "accept-language": [
    |            "en-US,en;q=0.9"
    |        ],
    |        "cache-control": [
    |            "max-age=0"
    |        ],
    |        "Host": [
    |            "t56efl9s9h.execute-api.us-east-1.amazonaws.com"
    |        ],
    |        "upgrade-insecure-requests": [
    |            "1"
    |        ],
    |        "User-Agent": [
    |            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36"
    |        ],
    |        "X-Amzn-Trace-Id": [
    |            "Root=1-5bc38070-75032a18c65115ec87392d7e"
    |        ],
    |        "X-Forwarded-For": [
    |            "71.239.243.2"
    |        ],
    |        "X-Forwarded-Port": [
    |            "443"
    |        ],
    |        "X-Forwarded-Proto": [
    |            "https"
    |        ]
    |    },
    |    "queryStringParameters": {
    |        "hi": "world"
    |    },
    |    "multiValueQueryStringParameters": {
    |        "hi": [
    |            "world"
    |        ]
    |    },
    |    "pathParameters": {
    |        "proxy": "fdfd"
    |    },
    |    "stageVariables": null,
    |    "requestContext": {
    |        "resourceId": "vzciam",
    |        "resourcePath": "/{proxy+}",
    |        "httpMethod": "GET",
    |        "extendedRequestId": "OxEBlEKmIAMFZKA=",
    |        "requestTime": "14/Oct/2018:17:44:16 +0000",
    |        "path": "/prod/fdfd",
    |        "accountId": "285992697021",
    |        "protocol": "HTTP/1.1",
    |        "stage": "prod",
    |        "requestTimeEpoch": 1539539056554,
    |        "requestId": "c557a15f-cfd8-11e8-bfaa-bb6c33143175",
    |        "identity": {
    |            "cognitoIdentityPoolId": null,
    |            "accountId": null,
    |            "cognitoIdentityId": null,
    |            "caller": null,
    |            "sourceIp": "71.239.243.2",
    |            "accessKey": null,
    |            "cognitoAuthenticationType": null,
    |            "cognitoAuthenticationProvider": null,
    |            "userArn": null,
    |            "userAgent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36",
    |            "user": null
    |        },
    |        "apiId": "t56efl9s9h"
    |    },
    |    "body": null,
    |    "isBase64Encoded": false
    |}""".stripMargin


//  val init = new LambdaInit()
//  val out = new FileOutputStream("out.json")
//  init.handleRequest(new ByteArrayInputStream(req.getBytes), out, null)

}