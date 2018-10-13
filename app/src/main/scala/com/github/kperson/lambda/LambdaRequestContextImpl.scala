package akka.http.scaladsl.server

// https://raw.githubusercontent.com/akka/akka-http/v10.1.5/akka-http/src/main/scala/akka/http/scaladsl/server/RequestContextImpl.scala

import scala.concurrent.{ExecutionContextExecutor, Future}
import akka.stream.{ActorMaterializer, Materializer}
import akka.event.LoggingAdapter
import akka.http.scaladsl.settings.{ParserSettings, RoutingSettings}
import akka.http.scaladsl.marshalling.{Marshal, ToResponseMarshallable}
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.util.FastFuture
import akka.http.scaladsl.util.FastFuture._
import com.github.kperson.lambda.LambdaHttpRequest


//I KNOW, THIS ISN'T GOOD
class LambdaRequestContextImpl(
  val lambdaHttpRequest: LambdaHttpRequest,
  val actorMaterializer: ActorMaterializer
) extends RequestContext {

  val request = lambdaHttpRequest.akkaHttpRequest
  val unmatchedPath = request.uri.path
  val executionContext: ExecutionContextExecutor = actorMaterializer.executionContext
  val log = RoutingLog.fromActorSystem(actorMaterializer.system).log
  val settings = RoutingSettings(actorMaterializer.system)
  val parserSettings =  ParserSettings(actorMaterializer.system)
  val materializer: Materializer = actorMaterializer

  def reconfigure(executionContext: ExecutionContextExecutor, materializer: Materializer, log: LoggingAdapter, settings: RoutingSettings): RequestContext =
    copy(executionContext = executionContext, materializer = materializer, log = log, routingSettings = settings)

  override def complete(trm: ToResponseMarshallable): Future[RouteResult] =
    trm(request)(executionContext)
      .fast.map(res ⇒ RouteResult.Complete(res))(executionContext)
      .fast.recover {
      case Marshal.UnacceptableResponseContentTypeException(supported) ⇒
        RouteResult.Rejected(UnacceptedResponseContentTypeRejection(supported) :: Nil)
      case RejectionError(rej) ⇒
        RouteResult.Rejected(rej :: Nil)
    }(executionContext)

  override def reject(rejections: Rejection*): Future[RouteResult] =
    FastFuture.successful(RouteResult.Rejected(rejections.toList))

  override def redirect(uri: Uri, redirectionType: Redirection): Future[RouteResult] = {
    //#red-impl
    complete(HttpResponse(
      status = redirectionType,
      headers = headers.Location(uri) :: Nil,
      entity = redirectionType.htmlTemplate match {
        case ""       ⇒ HttpEntity.Empty
        case template ⇒ HttpEntity(ContentTypes.`text/html(UTF-8)`, template format uri)
      }))
    //#red-impl
  }

  override def fail(error: Throwable): Future[RouteResult] =
    FastFuture.failed(error)

  override def withRequest(request: HttpRequest): RequestContext =
    if (request != this.request) copy(request = request) else this

  override def withExecutionContext(executionContext: ExecutionContextExecutor): RequestContext =
    if (executionContext != this.executionContext) copy(executionContext = executionContext) else this

  override def withMaterializer(materializer: Materializer): RequestContext =
    if (materializer != this.materializer) copy(materializer = materializer) else this

  override def withLog(log: LoggingAdapter): RequestContext =
    if (log != this.log) copy(log = log) else this

  override def withRoutingSettings(routingSettings: RoutingSettings): RequestContext =
    if (routingSettings != this.settings) copy(routingSettings = routingSettings) else this

  override def withParserSettings(parserSettings: ParserSettings): RequestContext =
    if (parserSettings != this.parserSettings) copy(parserSettings = parserSettings) else this

  override def mapRequest(f: HttpRequest ⇒ HttpRequest): RequestContext =
    copy(request = f(request))

  override def withUnmatchedPath(path: Uri.Path): RequestContext =
    if (path != unmatchedPath) copy(unmatchedPath = path) else this

  override def mapUnmatchedPath(f: Uri.Path ⇒ Uri.Path): RequestContext =
    copy(unmatchedPath = f(unmatchedPath))

  override def withAcceptAll: RequestContext = request.header[headers.Accept] match {
    case Some(accept @ headers.Accept(ranges)) if !accept.acceptsAll ⇒
      mapRequest(_.mapHeaders(_.map {
        case `accept` ⇒
          val acceptAll =
            if (ranges.exists(_.isWildcard)) ranges.map(r ⇒ if (r.isWildcard) MediaRanges.`*/*;q=MIN` else r)
            else ranges :+ MediaRanges.`*/*;q=MIN`
          accept.copy(mediaRanges = acceptAll)
        case x ⇒ x
      }))
    case _ ⇒ this
  }

  private def copy(
                    request:          HttpRequest              = request,
                    unmatchedPath:    Uri.Path                 = unmatchedPath,
                    executionContext: ExecutionContextExecutor = executionContext,
                    materializer:     Materializer             = materializer,
                    log:              LoggingAdapter           = log,
                    routingSettings:  RoutingSettings          = settings,
                    parserSettings:   ParserSettings           = parserSettings) =
    new RequestContextImpl(request, unmatchedPath, executionContext, materializer, log, routingSettings, parserSettings)

  override def toString: String =
    s"""RequestContext($request, $unmatchedPath, [more settings])"""
}