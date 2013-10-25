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

trait ReadLens[A, B] {

  def apply(a: A): B

  def in[C](second: ReadLens[C, A]): ReadLens[C, B] = {
    val first = ReadLens.this
    new ReadLens[C, B] {
      override def apply(c: C) = first(second(c))
    }
  }

  def and[C](second: ReadLens[A, C]): ReadLens[A, (B, C)] = {
    val first = ReadLens.this
    new ReadLens[A, (B, C)] {
      override def apply(a: A) = first(a) -> second(a)
    }
  }

  def map[C](f: B => C): ReadLens[A, C] = {
    val first = ReadLens.this
    new ReadLens[A, C] {
      override def apply(a: A) = f(first(a))
    }
  }
}

object ReadLens {
  def apply[A, B](_get: A => B): ReadLens[A, B] = new ReadLens[A, B] {
    override def apply(a: A) = _get(a)
  }
}

trait Lens[A, B] extends ReadLens[A, B] {

  def update(a: A, b: B): A

  def in[C](second: Lens[C, A]): Lens[C, B] = {
    val first = Lens.this
    new Lens[C, B] {

      override def apply(c: C) = first(second(c))

      override def update(c: C, b: B) = second(c) = first(second(c)) = b
    }
  }

  def and[C](second: Lens[A, C]): Lens[A, (B, C)] = {
    val first = Lens.this
    new Lens[A, (B, C)] {

      override def apply(a: A) = first(a) -> second(a)

      override def update(a: A, bc: (B, C)) = second(first(a) = bc._1) = bc._2
    }
  }
}

object Lens {
  def apply[A, B](_get: A => B, _set: (A, B) => A): Lens[A, B] = new Lens[A, B] {

    override def apply(a: A) = _get(a)

    override def update(a: A, b: B) = _set(a, b)
  }
}
