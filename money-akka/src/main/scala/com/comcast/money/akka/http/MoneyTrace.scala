/*
 * Copyright 2012 Comcast Cable Communications Management, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.comcast.money.akka.http

import akka.http.scaladsl.model.HttpEntity.ChunkStreamPart
import akka.http.scaladsl.model.{ ContentTypes, HttpEntity, HttpRequest, HttpResponse }
import akka.http.scaladsl.server.Directives.{ complete, extractRequest }
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.{ Sink, Source }
import com.comcast.money.akka.{ MoneyExtension, SpanContextWithStack, TraceContext }
import com.comcast.money.api.{ Note, SpanId }
import com.comcast.money.core.Formatters.fromHttpHeaders
import com.comcast.money.core.Tracer

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

/**
 * Traces a call to a instrumented Akka Http Routing DSL
 * The [[akka.http.scaladsl.server.Directive]] will execute application code and traces the time taken to fulfill a request.
 *
 * The directive will need to be the last directive used.
 *
 * The [[TraceContext]] created can be used to start and stop spans in other sections of the codebase
 */

object MoneyTrace {

  /**
   * Returns a Route to be used to complete a Akka Http Routing DSL
   *
   * Traces application code for a synchronous Request and Response
   *
   * NB: Only logic called by the function f will be traced within the requests Span
   *
   * Example:
   *
   * {{{
   *   get {
   *       pathSingleSlash {
   *         MoneyTrace {
   *           (tracedRequest: TracedRequest) => TracedResponse(HttpResponse(entity = "response"), tracedRequest.spanContext)
   *         }
   *       }
   *     }
   * }}}
   *
   * @param f              function from [[TracedRequest]] to [[TracedResponse]] should trigger all application code
   * @param moneyExtension [[akka.actor.ActorSystem]] extension to interact with [[com.comcast.money.core.Money]]
   * @param requestSKC     [[HttpRequestSpanKeyCreator]] to create a key for the Span generated in the Directive
   * @return [[Route]]
   */

  def apply(f: TracedRequest => TracedResponse)(implicit moneyExtension: MoneyExtension, requestSKC: HttpRequestSpanKeyCreator): Route =
    createDirective {
      (request, traceContext) =>

        val tracedResponse = f(TracedRequest(request, traceContext))

        traceContext.tracer.stopSpan(tracedResponse.isSuccess)
        complete(tracedResponse.response)
    }

  /**
   * returns a Route to complete a AkkaHttp routing DSL
   *
   * Traces asynchronous [[Future]] based application code
   *
   * NB: Only logic called by the function f will be traced within the requests Span
   *
   * {{{
   *   get {
   *       pathSingleSlash {
   *         MoneyTrace {
   *           (tracedRequest: TracedRequest) => Future(TracedResponse(HttpResponse(entity = "response"), tracedRequest.spanContext))
   *         }
   *       }
   *     }
   * }}}
   *
   * @param f                asynchronous application logic from TracedRequest to Future[TracedResponse]
   * @param moneyExtension [[akka.actor.ActorSystem]] extension to interact with [[com.comcast.money.core.Money]]
   * @param requestSKC     [[HttpRequestSpanKeyCreator]] to create a key for the Span generated in the Directive
   * @param executionContext the [[ExecutionContext]] for the [[Future]]
   * @return [[Route]]
   */

  def apply(f: TracedRequest => Future[TracedResponse])(implicit moneyExtension: MoneyExtension, requestSKC: HttpRequestSpanKeyCreator, executionContext: ExecutionContext): Route =
    createDirective {
      (request, traceContext) =>
        val eventualTracedResponse = f(TracedRequest(request, traceContext))

        eventualTracedResponse onComplete {
          case Success(tracedResponse) =>
            traceContext.tracer.stopSpan(tracedResponse.isSuccess)
          case Failure(e) =>
            traceContext.tracer.record(Note.of(e.getMessage, e.getStackTrace.toString))
            traceContext.tracer.stopSpan(result = false)
        }

        complete(eventualTracedResponse.map(_.response))
    }

  /**
   * Returns a Route to be used to complete a Akka Http Routing DSL
   *
   * Traces the completion of Stream being used to create a chunked Response to a client
   *
   * NB: Only logic called by the function f will be traced within the requests Span
   *
   * Example:
   *
   * {{{
   *   get {
   *       path("chunked") {
   *           MoneyTrace fromChunkedSource {
   *             (_: TracedRequest) => Source(List("a","b","c")).map(ChunkStreamPart(_))
   *           }
   *         }
   *     }
   * }}}
   *
   * @param f              application code that creates the [[Source]] to be used to complete the request
   * @param moneyExtension [[akka.actor.ActorSystem]] extension to interact with [[com.comcast.money.core.Money]]
   * @param requestSKC     [[HttpRequestSpanKeyCreator]] to create a key for the Span generated in the Directive
   * @return [[Route]]
   */

  def fromChunkedSource(f: TracedRequest => Source[ChunkStreamPart, _])(implicit moneyExtension: MoneyExtension, requestSKC: HttpRequestSpanKeyCreator): Route =
    createDirective {
      (request, traceContext) =>
        val source = f(TracedRequest(request, traceContext))

        val tracedSource = source alsoTo {
          Sink onComplete {
            case Success(_) => traceContext.tracer.stopSpan()
            case Failure(e) =>
              traceContext.tracer.record(Note.of(e.getMessage, e.getStackTrace.toString))
              traceContext.tracer.stopSpan(result = false)
          }
        }

        complete(HttpResponse(entity = HttpEntity.Chunked(ContentTypes.`text/plain(UTF-8)`, tracedSource)))
    }

  /**
   * INTERNAL API
   */

  /**
   * Returns [[Route]] for AkkaHttp Routing DSL
   *
   * Setup for tracing a [[HttpRequest]]
   *
   * Constructs a fresh [[TraceContext]] and builds a [[Tracer]] with the [[TraceContext]]
   *
   * The request is then checked for a existing [[SpanId]] if it exists a [[com.comcast.money.api.Span]]
   * is created from it and added to the [[TraceContext]] otherwise a fresh Span is started
   * and added to the [[TraceContext]]
   *
   * @param toRoute        the function that completes the [[HttpRequest]]
   * @param moneyExtension [[akka.actor.ActorSystem]] extension to interact with [[com.comcast.money.core.Money]]
   * @param requestSKC     [[HttpRequestSpanKeyCreator]] to create a key for the Span generated in the Directive
   * @return Route completing Routing DSL
   */

  private def createDirective(toRoute: (HttpRequest, TraceContext) => Route)(implicit moneyExtension: MoneyExtension, requestSKC: HttpRequestSpanKeyCreator): Route =
    extractRequest {
      request =>
        val traceContext: TraceContext = TraceContext(new SpanContextWithStack)

        maybeExtractHeaderSpanId(request) match {
          case Some(spanId) => traceContext.spanContext.push(traceContext.tracer.spanFactory.newSpan(spanId, requestSKC.httpRequestToKey(request)))
          case None => traceContext.tracer.startSpan(requestSKC.httpRequestToKey(request))
        }

        toRoute(request, traceContext)
    }

  /**
   * Returns a [[Option]] of [[SpanId]]
   *
   * returns [[Some]] when there is a [[SpanId]] present in the [[HttpRequest]] headers
   * returns [[None]] when there is not a [[SpanId]] present in the [[HttpRequest]] headers
   *
   * @param request [[HttpRequest]] that may contain a Money header
   * @return Option[SpanId]
   */

  private def maybeExtractHeaderSpanId(request: HttpRequest): Option[SpanId] =
    request
      .headers
      .flatMap(header => fromHttpHeaders(_ => header.value))
      .headOption
}

case class TracedRequest(request: HttpRequest, traceContext: TraceContext)

case class TracedResponse(response: HttpResponse, isSuccess: Boolean = true)
