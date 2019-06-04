package com.github.kperson

import scala.concurrent.Future

package object lambda {

  type RequestHandler = PartialFunction[(Method, String, LambdaHttpRequest), Future[LambdaHttpResponse]]

}
