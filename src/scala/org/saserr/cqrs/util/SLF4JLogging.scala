/*
 * Copyright 2013 Sanjin Sehic
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

package org.saserr.cqrs.util

import org.slf4j.{LoggerFactory => SLF4JLoggerFactory}

trait SLF4JLogging extends Logging {
  override protected lazy val logger = new LoggerFactory {
    override def create(name: Logger.Name) = new Logger {

      import org.saserr.cqrs.util.Logging.Level._

      @transient private[this] lazy val log = SLF4JLoggerFactory.getLogger(name)

      override final def isEnabled(level: Logging.Level) = level match {
        case Debug => log.isDebugEnabled
        case Info => log.isInfoEnabled
        case Warn => log.isWarnEnabled
        case Error => log.isErrorEnabled
      }

      override final def logAt(level: Logging.Level)(message: => String, cause: Option[Throwable] = None) {
        if (isEnabled(level))
          cause match {
            case Some(exception) => level match {
              case Debug => log.debug(message, exception)
              case Info => log.info(message, exception)
              case Warn => log.warn(message, exception)
              case Error => log.error(message, exception)
            }
            case _ => level match {
              case Debug => log.debug(message)
              case Info => log.info(message)
              case Warn => log.warn(message)
              case Error => log.error(message)
            }
          }
      }
    }
  }
}
