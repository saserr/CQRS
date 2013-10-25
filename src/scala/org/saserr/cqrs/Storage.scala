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

import scala.language.higherKinds

import java.io.Serializable

import scala.collection.immutable.{Set, Traversable, Vector}

import scalaz.@>

abstract class Storage[Key, Value <: Serializable, F[A] <: Traversable[A]]
(private val version: Long, private val vals: F[Versioned[Value]]) extends Serializable {

  @transient protected def key: Value @> Key

  @transient private[this] lazy val byKey = vals.map(v => key.get(v.value) -> v).toMap
  @transient lazy val values = vals.to[Vector].map(_.value)
  @transient lazy val keys: Set[Key] = byKey.keySet

  def contains(key: Key): Boolean = byKey contains key

  def get(key: Key): Option[Versioned[Value]] = byKey get key
}

object Storage {

  implicit def version[Key <: Serializable, Value <: Serializable, F[A] <: Traversable[A]]: Version[Storage[Key, Value, F]] =
    new Version[Storage[Key, Value, F]] {
      override def apply(storage: Storage[Key, Value, F]) = storage.version
    }

  implicit def read[Key <: Serializable, Value <: Serializable, F[A] <: Traversable[A], S <: Serializable]: State.Read[Key, Value, Storage[Key, Value, F]] =
    new State.Read[Key, Value, Storage[Key, Value, F]] {

      override def get(key: Key, storage: Storage[Key, Value, F]) = storage get key

      override def values(storage: Storage[Key, Value, F]) = Versioned(storage.version, storage.values)
    }
}
