libraryDependencies ++= Seq(
  "com.github.j5ik2o" %% "reactive-aws-ecs-core" % "1.1.3"
)
addSbtPlugin("io.gatling" % "gatling-sbt" % "3.1.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.7.0")

addSbtPlugin("com.lightbend.sbt" % "sbt-javaagent" % "0.1.4")

addSbtPlugin("com.mintbeans" % "sbt-ecr" % "0.15.0")

addSbtPlugin("io.spray" % "sbt-revolver" % "0.9.1")

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.6")

addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.12.1")
