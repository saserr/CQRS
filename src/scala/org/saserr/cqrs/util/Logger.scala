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

trait Logger {

  import org.saserr.cqrs.util.Logging.Level._

  def isDebugEnabled: Boolean = isEnabled(Debug)

  def debug(message: => String, cause: Option[Throwable] = None) {
    if (isDebugEnabled) logAt(Debug)(message, cause)
  }

  def isInfoEnabled: Boolean = isEnabled(Info)

  def info(message: => String, cause: Option[Throwable] = None) {
    if (isInfoEnabled) logAt(Info)(message, cause)
  }

  def isWarnEnabled: Boolean = isEnabled(Warn)

  def warn(message: => String, cause: Option[Throwable] = None) {
    if (isWarnEnabled) logAt(Warn)(message, cause)
  }

  def isErrorEnabled: Boolean = isEnabled(Error)

  def error(message: => String, cause: Option[Throwable] = None) {
    if (isErrorEnabled) logAt(Error)(message, cause)
  }

  def isEnabled(level: Logging.Level): Boolean =
    level match {
      case Debug => isDebugEnabled
      case Info => isInfoEnabled
      case Warn => isWarnEnabled
      case Error => isErrorEnabled
    }

  def logAt(level: Logging.Level)(message: => String, cause: Option[Throwable] = None) {
    if (isEnabled(level))
      level match {
        case Debug => debug(message, cause)
        case Info => info(message, cause)
        case Warn => warn(message, cause)
        case Error => error(message, cause)
      }
  }
}

object Logger {
  type Name = String
}
