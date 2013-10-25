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

import scala.reflect.ClassTag
import scala.reflect.ManifestFactory.classType

import org.scalatest.mock.MockitoSugar

import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer

trait Mocking {

  def mock[A <: AnyRef](name: String)(implicit ct: ClassTag[A]): A =
    MockitoSugar.mock[A](name)(classType(ct.runtimeClass))

  implicit def functionToAnswer[A](f: Array[AnyRef] => A): Answer[A] = new Answer[A] {
    override def answer(invocation: InvocationOnMock) = f(invocation.getArguments)
  }
}
