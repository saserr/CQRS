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

import java.io.Serializable

import scala.annotation.tailrec
import scala.collection.immutable.{List, Set, SortedSet, Traversable}
import scala.concurrent.Future
import scala.concurrent.stm.{InTxn, Ref, Source}

import akka.actor.{Actor, ActorRef, ActorSelection}
import akka.pattern.ask
import akka.util.Timeout

import org.eligosource.eventsourced.core.{actor, Eventsourced, Message, ReplayParams, SnapshotOffer, SnapshotRequest}
import org.eligosource.eventsourced.core.JournalProtocol.{Loop, ReplayDone, ReplayInMsgs, WriteInMsg, Written}

import org.saserr.cqrs.util.{Logging, Timer, Type, Typed}

abstract class State[BaseEvent <: Serializable, Storage <: Serializable](init: Storage)
                                                                        (implicit BaseEvent: Type[BaseEvent],
                                                                         Storage: Type[Storage],
                                                                         version: Version[Storage])
  extends EventHandling[BaseEvent, Storage] with Logging {

  protected def name: String

  protected type Command[Payload] = cqrs.Command[BaseEvent, Payload, Storage]

  private[this] val log = logger.create(s"CQRS.State[$name]")
  private[this] val data = Ref(init)

  object Protocol {

    case class Execute[Payload](version: Long, payload: Payload)(implicit command: Command[Payload]) {

      def apply(commited: List[Typed[_ <: BaseEvent]], storage: Storage, replyTo: ActorRef)
               (onSuccess: Set[Typed[_ <: BaseEvent]] => Unit) {
        command(payload, storage) match {
          case Command.Result.Failure(reason) => replyTo ! State.Result.Failure(reason)
          case Command.Result.Success(toCommit) if toCommit.isEmpty => replyTo ! State.Result.Success
          case Command.Result.Success(toCommit) =>
            log.debug(s"Command $payload produced $toCommit")
            conflicts(toCommit.toList, commited) match {
              case Execute.Resolution.Ok(events) => onSuccess(events)
              case Execute.Resolution.Failure(reason) => replyTo ! State.Result.Failure(reason)
              case Execute.Resolution.Conflicts(reason, event) => replyTo ! State.Result.Conflict(reason, event)
            }
        }
      }

      @tailrec
      private def conflicts(toCommit: List[Typed[_ <: BaseEvent]],
                            commited: List[Typed[_ <: BaseEvent]],
                            result: Set[Typed[_ <: BaseEvent]] = Set.empty): Execute.Resolution = {
        @tailrec
        def step(toCommit: Typed[_ <: BaseEvent],
                 commited: List[Typed[_ <: BaseEvent]],
                 result: Execute.Resolution): Execute.Resolution =
          commited match {
            case event :: rest => State.this.conflicts(toCommit, event) match {
              case Some(Event.Resolution.Ok) =>
                step(toCommit, rest, result)
              case Some(Event.Resolution.Remove) =>
                step(toCommit, rest, Execute.Resolution.Ok(Set.empty))
              case Some(Event.Resolution.Replace(replaced)) =>
                step(replaced, rest, Execute.Resolution.Ok(Set(replaced)))
              case Some(Event.Resolution.Conflicts(reason)) =>
                Execute.Resolution.Conflicts(reason, event.value)
              case None => Execute.Resolution.Failure(s"Unknown event: $toCommit")
            }
            case Nil => result
          }

        toCommit match {
          case event :: rest => step(event, commited, Execute.Resolution.Ok(Set(event))) match {
            case Execute.Resolution.Ok(events) => conflicts(rest, commited, result ++ events)
            case other =>
              log.warn(s"Resolution was $other")
              other
          }
          case Nil =>
            val ok = Execute.Resolution.Ok(result)
            log.debug(s"Resolution was $ok")
            ok
        }
      }
    }

    object Execute {

      def apply[Payload](payload: Versioned[Payload])(implicit command: Command[Payload]): Execute[Payload] =
        new Execute[Payload](payload.version, payload.value)

      sealed trait Resolution

      object Resolution {

        case class Ok(events: Set[Typed[_ <: BaseEvent]]) extends Resolution

        case class Failure(reason: String) extends Resolution

        case class Conflicts(reason: String, event: BaseEvent) extends Resolution

      }

    }

    case class Gathered[A](last: Long, commited: List[Typed[_ <: BaseEvent]], execute: Execute[A], replyTo: ActorRef)

    case class Return(result: State.Result[BaseEvent], replyTo: ActorRef)

  }

  object Actor {
    def apply(_id: Int): Actor = new _actor with Eventsourced {
      override val id = _id
    }
  }


  abstract class _actor extends Actor {
    this: Eventsourced =>

    private[this] var recovering = true

    private[this] val base: Receive = {
      case message: Message if (classOf[Typed[_]] isInstance message.event) && (message.event.asInstanceOf[Typed[_]].tpe <:< BaseEvent) =>
        val current = message.sequenceNr
        val event = message.event.asInstanceOf[Typed[BaseEvent]]
        data.single.transform {
          storage =>
            if (version(storage) < current) {
              update(current, event, storage) getOrElse {
                log.error(s"[$name] Received unknown event: $event! Stopping")
                context.stop(self)
                storage
              }
            } else {
              log.warn(s"[$name] Received message from past: $message")
              storage
            }
        }
      case message: Message =>
        log.error(s"[$name] Received unknown event type: ${message.event.getClass.getSimpleName}! Stopping")
        context.stop(self)
    }

    private[this] val failedRecovery: Receive = {
      case _: Message => /* ignore */
      case _: SnapshotOffer => /* ignore */
      case ReplayDone =>
        unbecome()
        data.single.set(init)
        journal ! ReplayInMsgs(ReplayParams(id), self)
      case execute@Protocol.Execute(_, _) => journal forward Loop(execute, self)
      case unknown => log.warn(s"[$name] Received unknown message: $unknown")
    }

    private[this] def recovery(timer: Timer): Receive = {
      case SnapshotOffer(snapshot) if (classOf[Typed[_]] isInstance snapshot.state) && (snapshot.state.asInstanceOf[Typed[_]].tpe <:< Storage) =>
        log.debug(s"[$name] Received snapshot: ${snapshot.state}")
        data.single.set(snapshot.state.asInstanceOf[Typed[Storage]].value)
      case SnapshotOffer(snapshot) =>
        log.warn(s"[$name] Received unknown snapshot type: ${snapshot.state.getClass.getSimpleName}! Recovering from scratch")
        become(failedRecovery, discardOld = false)
      case ReplayDone =>
        log.debug(s"[$name] Recovered in ${timer.split().getMillis}ms")
        recovering = false
        unbecome()
        context.parent ! ReplayDone
      case execute@Protocol.Execute(_, _) => journal forward Loop(execute, self)
      case unknown => log.warn(s"[$name] Received unknown message: $unknown")
    }

    override def preStart() {
      log.debug(s"[$name] Started recovery")
      recovering = true
      become(base orElse recovery(Timer()))
      journal ! ReplayInMsgs(ReplayParams(id, snapshot = true), self)
    }

    override def postStop() {
      if (recovering) context.parent ! ReplayDone
    }

    override def preRestart(reason: Throwable, message: Option[Any]) {
      log.debug(s"[$name] Started restart")
      super.preRestart(reason, message)
      if (!recovering) sender ! State.Result.Failure("Request cannot be handled! Please retry")
    }

    override def postRestart(reason: Throwable) {
      log.warn(s"[$name] Restarted")
      recovering = false
    }

    override def receive = base orElse {
      case execute@Protocol.Execute(seen, command) =>
        log.debug(s"[$name] Received command $command")
        val replyTo = sender
        val storage = data.single.get
        if (version(storage) == seen) execute(List.empty, storage, replyTo)(onSuccess = persist(replyTo))
        else actor(new Gather(id, journal, execute, replyTo)) // there were changes
      case gathered@Protocol.Gathered(last, commited, execute, replyTo) =>
        log.debug(s"[$name] Received: $gathered")
        val storage = data.single.get
        if (version(storage) == last) execute(commited, storage, replyTo)(onSuccess = persist(replyTo))
        else actor(new Gather(id, journal, execute, replyTo)) // there were changes during retrieval so we must redo it
      case Protocol.Return(result, replyTo) => replyTo ! result
      case request: SnapshotRequest => request.process(Typed(data.single.get))
      case unknown => log.warn(s"[$name] Received unknown message: $unknown")
    }

    private def persist(replyTo: ActorRef)(events: Set[Typed[_ <: BaseEvent]]) {
      log.info(s"[$name] Persisting ${events.size} events")
      for (event <- events) journal ! WriteInMsg(id, Message(event).copy(processorId = id), self)
      journal ! Loop(Protocol.Return(State.Result.Success, replyTo), self)
    }
  }

  class Methods[Key, Value](actor: ActorSelection)
                           (implicit read: State.Read[Key, Value, Storage]) extends Source[Storage] {

    def version(implicit txn: InTxn): Long = State.this.version(data.get)

    def apply(key: Key)(implicit txn: InTxn): Option[Versioned[Value]] = read.get(key, data.get)

    def all(implicit txn: InTxn): Versioned[Traversable[Value]] = read.values(data.get)

    override def get(implicit txn: InTxn) = data.get

    override def getWith[Z](f: Storage => Z)(implicit txn: InTxn) = data.getWith(f)

    override def relaxedGet(equiv: (Storage, Storage) => Boolean)(implicit txn: InTxn) = data.relaxedGet(equiv)

    override def single = data.single

    def execute[Payload: Command](payload: Versioned[Payload])(implicit timeout: Timeout): Future[State.Result[BaseEvent]] =
      (actor ? Protocol.Execute(payload)).mapTo[State.Result[BaseEvent]]
  }

  private class Gather[A](id: Int, journal: ActorRef, execute: Protocol.Execute[A], replyTo: ActorRef) extends Actor {

    private[this] var commited = SortedSet.empty[(Long, Typed[_ <: BaseEvent])](Ordering.by(_._1))

    private[this] var last: Long = execute.version

    override def preStart() {
      journal ! ReplayInMsgs(id, execute.version + 1, self)
    }

    override def receive = {
      case Written(message) if (classOf[Typed[_]] isInstance message.event) && (message.event.asInstanceOf[Typed[_]].tpe <:< BaseEvent) =>
        commited = commited + (message.sequenceNr -> message.event.asInstanceOf[Typed[BaseEvent]])
        if (last < message.sequenceNr) last = message.sequenceNr
      case Written(message) =>
        log.error(s"Received unknown event type: ${message.event.getClass.getSimpleName}! Stopping")
        replyTo ! State.Result.Failure("Request cannot be handled! Please retry")
        context.stop(self)
      case ReplayDone =>
        context.parent ! Protocol.Gathered(last, commited.toList.map(_._2), execute, replyTo)
        context.stop(self)
    }
  }

}

object State {

  sealed trait Result[+Event]

  object Result {

    case object Success extends Result[Nothing]

    case class Failure(reason: String) extends Result[Nothing]

    case class Conflict[Event](reason: String, event: Event) extends Result[Event]

  }

  trait Read[Key, Value, -Storage] {

    def get(key: Key, storage: Storage): Option[Versioned[Value]]

    def values(storage: Storage): Versioned[Traversable[Value]]
  }

}
