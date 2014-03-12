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

import sbt._
import sbt.Keys._

object Info {

  val version = "0.1-SNAPSHOT"

  val settings: Seq[Setting[_]] = Seq(
    Keys.version := version,
    organization := "org.saserr.cqrs",
    licenses +=("Apache License Version 2.0", url("http://www.apache.org/licenses/LICENSE-2.0")),
    startYear := Some(2013)
  )
}

object Layout {
  val settings: Seq[Setting[_]] = Seq(
    sourceDirectory in Compile <<= baseDirectory(_ / "src"),
    sourceDirectory in Test <<= baseDirectory(_ / "test")
  )
}

object Build {

  val version = "2.10.3"

  val settings: Seq[Setting[_]] = Seq(
    scalaVersion := version,
    scalacOptions ++= Seq("-target:jvm-1.7", "-deprecation", "-unchecked", "-feature",
                          "-Xlog-reflective-calls", "-Ywarn-adapted-args", "-encoding", "utf8"),
    javacOptions ++= Seq("-target", "7", "-source", "7", "-encoding", "utf8")
  )
}

// Shell prompt which show the current project and build version
object Shell {
  def project(implicit state: State) = Project.extract(state).currentProject.id
  val settings: Seq[Setting[_]] = Seq(
    shellPrompt := {implicit state => s"$project:${Info.version}> "}
  )
}

object Dependency {

  object Scala {
    val reflect = "org.scala-lang" % "scala-reflect" % Build.version
  }

  object ScalaSTM {
    val version = "0.7"
    val core = "org.scala-stm" %% "scala-stm" % version
  }

  object Scalaz {
    val version = "7.0.6"
    val core = "org.scalaz" %% "scalaz-core" % version
    val iteratee = "org.scalaz" %% "scalaz-iteratee" % version
  }

  object Akka {
    val version = "2.3.0"
    val actor = "com.typesafe.akka" %% "akka-actor" % version
    val testkit = "com.typesafe.akka" %% "akka-testkit" % version
  }

  object Eventsourced {
    val version = "0.6.0"
    val resolver = "Eligosource Releases" at "http://repo.eligotech.com/nexus/content/repositories/eligosource-releases"
    val core = "org.eligosource" %% "eventsourced-core" % version
    object Journal {
      val InMemory = "org.eligosource" %% "eventsourced-journal-inmem" % version
      val LevelDB = "org.eligosource" %% "eventsourced-journal-leveldb" % version
    }
  }

  object Joda {
    val convert = "org.joda" % "joda-convert" % "1.6"
    val time = "joda-time" % "joda-time" % "2.3"
  }

  object SLF4J {
    val version = "1.7.6"
    val api = "org.slf4j" % "slf4j-api" % version
  }

  object Logback {
    val version = "1.1.1"
    val classic = "ch.qos.logback" % "logback-classic" % version
  }

  object ScalaTest {
    val version = "2.1.0"
    val core = "org.scalatest" %% "scalatest" % version
  }

  object Mockito {
    val version = "1.9.5"
    val all = "org.mockito" % "mockito-all" % version
  }
}

object CQRS extends Build {

  import Dependency._

  lazy val project = Project(
    "cqrs",
    file("."),
    settings = Defaults.defaultSettings ++
               Info.settings ++
               Layout.settings ++
               Build.settings ++
               Shell.settings ++
               Seq(
                 resolvers ++= Seq(Eventsourced.resolver),
                 libraryDependencies ++= Seq(Scala.reflect,
                                             ScalaSTM.core,
                                             Scalaz.core,
                                             Scalaz.iteratee,
                                             Akka.actor,
                                             Eventsourced.core,
                                             Joda.time,
                                             Joda.convert,
                                             SLF4J.api,
                                             ScalaTest.core % "test",
                                             Mockito.all % "test",
                                             Akka.testkit % "test",
                                             Eventsourced.Journal.InMemory % "test",
                                             Eventsourced.Journal.LevelDB % "runtime",
                                             Logback.classic % "runtime")
               )
  )
}
