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

import scala.collection.immutable.Set

import org.saserr.cqrs.util.Typed

trait Command[BaseEvent, Message, Storage] extends ((Message, Storage) => Command.Result[BaseEvent]) {

  override def apply(message: Message, storage: Storage): Command.Result[BaseEvent]

  protected val Result = Command.Result
}

object Command {

  sealed trait Result[+BaseEvent]

  object Result {

    case class Failure(reason: String) extends Result[Nothing]

    case class Success[BaseEvent](events: Set[Typed[_ <: BaseEvent]]) extends Result[BaseEvent] {
      def +[AnotherBase >: BaseEvent, Event <: AnotherBase](event: Typed[Event]): Success[AnotherBase] =
        new Success[AnotherBase](events.asInstanceOf[Set[Typed[_ <: AnotherBase]]] + event)
    }

    object Success {

      def apply[BaseEvent](): Success[BaseEvent] = new Success[BaseEvent](Set.empty)

      def apply[Event](event: Typed[Event]): Success[Event] = new Success[Event](Set(event))
    }

  }

}
