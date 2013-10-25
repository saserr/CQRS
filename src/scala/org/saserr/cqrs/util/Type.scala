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

import scala.language.implicitConversions

import java.io.{ObjectStreamException, Serializable}

import scala.collection.immutable.{Seq, Vector}
import scala.reflect.ClassTag
import scala.reflect.api.{Mirror, TypeCreator, Universe}
import scala.reflect.ManifestFactory.classType
import scala.reflect.runtime.currentMirror
import scala.reflect.runtime.universe.{manifestToTypeTag, TypeRef, TypeTag}

trait Type[A] extends Serializable with Equals {

  def classTag: ClassTag[A]

  def manifest: Manifest[A]

  def typeTag: TypeTag[A]

  def klass: Class[A]

  def arguments: Seq[Type[_]] = {
    val manifests = manifest.typeArguments.to[Vector].map(_.asInstanceOf[Manifest[Any]])
    val typeTags = typeTag.tpe match {
      case TypeRef(_, _, tags) => tags.to[Vector] map {
        tag => TypeTag(currentMirror, new TypeCreator {
          override def apply[U <: Universe with Singleton](m: Mirror[U]) = tag.asInstanceOf[U#Type]
        }).asInstanceOf[TypeTag[Any]]
      }
      case _ => Vector.empty
    }

    (manifests zip typeTags) map {
      case (manifest, typeTag) => Type(manifest, typeTag)
    }
  }

  def <:<[B](other: Type[B]): Boolean = this.typeTag.tpe <:< other.typeTag.tpe

  override lazy val hashCode = classTag.hashCode()

  override def equals(other: Any) = other match {
    case that: Type[_] => (that canEqual this) && (this <:< that) && (that <:< this)
    case _ => false
  }

  override def canEqual(other: Any) = other.isInstanceOf[Type[_]]

  override lazy val toString = typeTag.tpe.toString

  @throws[ObjectStreamException] def writeReplace(): AnyRef = new Type.Serialized(klass.getName, arguments)
}

object Type {

  def apply[A](implicit _manifest: Manifest[A], _typeTag: TypeTag[A]): Type[A] = new Type[A] {

    override val typeTag = _typeTag
    override val manifest = _manifest

    override lazy val classTag = ClassTag(_manifest.runtimeClass).asInstanceOf[ClassTag[A]]
    override lazy val klass = _manifest.runtimeClass.asInstanceOf[Class[A]]
  }

  implicit def fromAny[A: Manifest : TypeTag]: Type[A] = apply[A]

  private def apply[A](name: String, args: Seq[Type[_]]): Type[A] = {
    val klass = (name match {
      case "boolean" => java.lang.Boolean.TYPE
      case "byte" => java.lang.Byte.TYPE
      case "char" => java.lang.Character.TYPE
      case "double" => java.lang.Double.TYPE
      case "float" => java.lang.Float.TYPE
      case "int" => java.lang.Integer.TYPE
      case "long" => java.lang.Long.TYPE
      case "short" => java.lang.Short.TYPE
      case _ => getClass.getClassLoader.loadClass(name)
    }).asInstanceOf[Class[A]]
    val manifest =
      (if (args.isEmpty) classType(klass)
      else classType(klass, args.head.manifest, args.tail.map(_.manifest): _*)).asInstanceOf[Manifest[A]]
    val typeTag = manifestToTypeTag(currentMirror, manifest).asInstanceOf[TypeTag[A]]

    apply[A](manifest, typeTag)
  }

  private class Serialized[A](name: String, arguments: Seq[Type[_]]) extends Serializable {
    @throws[ObjectStreamException] def readResolve(): AnyRef = Type[A](name, arguments)
  }

}

case class Typed[A](value: A)(implicit val tpe: Type[A])

object Typed {
  implicit def fromValue[A: Type](value: A): Typed[A] = Typed(value)
}
