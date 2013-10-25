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
package util

import java.io._

import scala.reflect.classTag
import scala.reflect.runtime.universe.typeTag

class TypeSpec extends Spec {

  class A

  class U extends A

  class B

  class D[X]

  class E[X, Y]

  def serialize[X](implicit typeA: Type[X]): Array[Byte] = {
    val out = new ByteArrayOutputStream()

    try new ObjectOutputStream(out).writeObject(typeA)
    finally out.close()

    out.toByteArray
  }

  def deserialize(bytes: Array[Byte]): Type[_] = {
    val in = new ByteArrayInputStream(bytes)

    try new ObjectInputStream(in).readObject().asInstanceOf[Type[_]]
    finally in.close()
  }

  "Type" should {
    "return the correct ClassTag" in {
      Type[A].classTag should be(classTag[A])
    }

    "return the correct TypeTag" in {
      Type[A].typeTag should be(typeTag[A])
    }

    "return the correct Manifest" in {
      Type[A].manifest should be(manifest[A])
    }

    "return the correct Class" in {
      Type[A].klass should be(classOf[A])
    }

    "return no generic argument Types when it has none" in {
      val typeA = Type[A]
      typeA.arguments should be(empty)
    }

    "return its generic arguments as Types when it has them" in {
      val typeDA = Type[D[A]]

      typeDA.arguments should have length 1
      typeDA.arguments(0) should be(Type[A])
    }

    "be equal to itself" in {
      (Type[A] == Type[A]) should be(true)
    }

    "be different" when {
      "compared to other type" in {
        (Type[A] == Type[B]) should be(false)
      }

      "its generic types are different" in {
        (Type[D[A]] == Type[D[B]]) should be(false)
      }
    }
  }

  "Type.<:<" should {
    "return true" when {
      "compared with a same type" in {
        (Type[A] <:< Type[A]) should be(true)
      }

      "compared with a super type" in {
        (Type[U] <:< Type[A]) should be(true)
      }
    }

    "return false" when {
      "compared with a sub type" in {
        (Type[A] <:< Type[U]) should be(false)
      }

      "compared with a different type" in {
        (Type[A] <:< Type[B]) should be(false)
      }
    }
  }

  "Type" when {
    "serialized and then deserialized" should {
      "be same for boolean" in {
        deserialize(serialize[Boolean]) should be(Type[Boolean])
      }

      "be same for byte" in {
        deserialize(serialize[Byte]) should be(Type[Byte])
      }

      "be same for char" in {
        deserialize(serialize[Char]) should be(Type[Char])
      }

      "be same for double" in {
        deserialize(serialize[Double]) should be(Type[Double])
      }

      "be same for float" in {
        deserialize(serialize[Float]) should be(Type[Float])
      }

      "be same for int" in {
        deserialize(serialize[Int]) should be(Type[Int])
      }

      "be same for long" in {
        deserialize(serialize[Long]) should be(Type[Long])
      }

      "be same for short" in {
        deserialize(serialize[Short]) should be(Type[Short])
      }

      "be same for simple type" in {
        deserialize(serialize[A]) should be(Type[A])
      }

      "be same for complex type" in {
        deserialize(serialize[E[D[A], B]]) should be(Type[E[D[A], B]])
      }
    }
  }
}
