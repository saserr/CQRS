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

import org.saserr.cqrs.util.Lens

trait Version[-Value] {
  def apply(value: Value): Long
}

object Version {
  val key: String = "version"
}

case class Versioned[+Value](version: Long, value: Value) {
  def map[A](f: Value => A): Versioned[A] = Versioned(version, f(value))
}

object Versioned {

  def version[A]: Lens[Versioned[A], Long] = Lens(_.version, (versioned, version) => versioned.copy(version = version))

  def value[A]: Lens[Versioned[A], A] = Lens(_.value, (versioned, value) => versioned.copy(value = value))

  implicit def hasVersion[A]: Version[Versioned[A]] = new Version[Versioned[A]] {
    override def apply(value: Versioned[A]) = value.version
  }
}
