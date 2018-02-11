addSbtPlugin("com.typesafe.sbt" % "sbt-git" % "0.9.3")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.1")
libraryDependencies += "org.vafer" % "jdeb" % "1.3" artifacts (Artifact("jdeb", "jar", "jar"))

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.6")
