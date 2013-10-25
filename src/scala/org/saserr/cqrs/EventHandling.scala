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

package org.saserr
package cqrs

import scala.collection.immutable.Map

import org.saserr.cqrs.util.{Type, Typed}

trait EventHandling[BaseEvent, Storage] {

  protected type Event[A <: BaseEvent] = cqrs.Event[BaseEvent, A, Storage]
  protected type Events = PartialFunction[Type[_ <: BaseEvent], Event[_ <: BaseEvent]]

  protected def events: Events

  protected class EventsHelper(private val events: Map[Type[_ <: BaseEvent], Event[_ <: BaseEvent]]) extends Events {

    override def apply(tpe: Type[_ <: BaseEvent]) = events(tpe)

    override def isDefinedAt(tpe: Type[_ <: BaseEvent]) = events.contains(tpe)

    def +(other: EventsHelper): EventsHelper = new EventsHelper(events ++ other.events)
  }

  protected def event[A <: BaseEvent](implicit A: Type[A], event: Event[A]): EventsHelper =
    new EventsHelper(Map(A -> event))

  private def event[A <: BaseEvent](implicit A: Type[A]): Option[Event[A]] =
    if (events.isDefinedAt(A)) {
      val result = events(A)
      if (A == result.tpe) Some(result.asInstanceOf[Event[A]])
      else None
    } else None

  protected def conflicts[A <: BaseEvent, B <: BaseEvent](toCommit: Typed[A], commited: Typed[B]): Option[Event.Resolution[BaseEvent]] =
    event(toCommit.tpe).map(_.conflicts(toCommit.value, commited.value)(commited.tpe))

  def update[Event <: BaseEvent](version: Long, toCommit: Typed[Event], storage: Storage): Option[Storage] =
      event(toCommit.tpe).map(_.update(version, toCommit.value, storage))
}
