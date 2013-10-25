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

package org.saserr.cqrs

import org.saserr.cqrs.util.{Type, Typed}

trait Event[BaseEvent, A <: BaseEvent, Storage] {

  def tpe: Type[A]

  def conflicts[B <: BaseEvent : Type](toCommit: A, committed: B): Event.Resolution[BaseEvent]

  def update(version: Long, event: A, storage: Storage): Storage

  protected val Resolution = Event.Resolution
}

object Event {

  sealed trait Resolution[+Event]

  object Resolution {

    case object Ok extends Resolution[Nothing]

    case object Remove extends Resolution[Nothing]

    case class Replace[Event](event: Typed[Event]) extends Resolution[Event]

    case class Conflicts(reason: String) extends Resolution[Nothing]

  }

}
