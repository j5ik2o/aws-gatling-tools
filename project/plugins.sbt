resolvers ++= Seq(
  "Sonatype OSS Snapshot Repository" at "https://oss.sonatype.org/content/repositories/snapshots/",
  "Sonatype OSS Release Repository" at "https://oss.sonatype.org/content/repositories/releases/",
  "Seasar Repository" at "https://maven.seasar.org/maven2/",
  Resolver.bintrayRepo("kamon-io", "sbt-plugins")
)
libraryDependencies ++= Seq(
  "com.h2database" % "h2" % "1.4.195",
  "commons-io" % "commons-io" % "2.5",
  "org.seasar.util" % "s2util" % "0.0.1",
  "com.github.j5ik2o" %% "reactive-aws-ecs-core" % "1.1.3"
)
addSbtPlugin("io.gatling" % "gatling-sbt" % "3.1.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.7.0")

addSbtPlugin("com.lightbend.sbt" % "sbt-javaagent" % "0.1.4")

addSbtPlugin("com.mintbeans" % "sbt-ecr" % "0.15.0")

addSbtPlugin("io.spray" % "sbt-revolver" % "0.9.1")
