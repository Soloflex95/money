/*
 * Copyright 2012-2015 Comcast Cable Communications Management, LLC
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

package com.comcast.money.core.async

import java.util.ServiceLoader

import scala.collection.JavaConverters._

object AsyncTracingService {
  private lazy val services = loadServices()

  def findTracingService(future: AnyRef): Option[AsyncTracingService] =
    services.find(_.supports(future))

  private def loadServices() =
    ServiceLoader.load(classOf[AsyncTracingService], classOf[AsyncTracingService].getClassLoader).asScala.toList
}

trait AsyncTracingService {
  def supports(future: AnyRef): Boolean
  def whenDone(future: AnyRef, f: (Any, Throwable) => Unit): AnyRef
}
