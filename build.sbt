name := "lykke-waves-blockchain-scanner"

organization := "ru.tolsi"

version in ThisBuild := {
  if (git.gitCurrentTags.value.nonEmpty) {
    git.gitDescribedVersion.value.get
  } else {
    if (git.gitHeadCommit.value.contains(git.gitCurrentBranch.value)) {
      git.gitHeadCommit.value.get.take(8) + "-SNAPSHOT"
    } else {
      git.gitCurrentBranch.value + "-" + git.gitHeadCommit.value.get.take(8) + "-SNAPSHOT"
    }
  }
}

libraryDependencies ++= Seq(
  "ru.tolsi" %% "lykke-waves-common" % "master-dbad561d-SNAPSHOT" % "compile->compile;test->test",
  "com.github.salat" %% "salat" % "1.11.2",
  "org.mongodb" %% "casbah" % "3.1.1",
  "com.github.fakemongo" % "fongo" % "2.1.0" % "test",
  "org.scalatest" %% "scalatest" % "3.0.5" % "test"
)

sourceGenerators in Compile += Def.task {
  val projectInfoFile = (sourceManaged in Compile).value / "ru" / "tolsi" / "lykke" / "waves" / "blockchainapi" / "Version.scala"
  IO.write(projectInfoFile,
    s"""package ru.tolsi.lykke.waves.blockchainscanner
       |
       |object ProjectInfo {
       |  val VersionString = "${version.value}"
       |  val NameString = "${name.value}"
       |}
       |""".stripMargin)
  Seq(projectInfoFile)
}

scalaVersion := "2.12.4"

// package
enablePlugins(JavaServerAppPackaging, DebianPlugin, JDebPackaging, GitVersioning)

javaOptions in Universal ++= Seq(
  // -J prefix is required by the bash script
  "-J-server",
  // JVM memory tuning for 1g ram
  "-J-Xms128m",
  "-J-Xmx1g",

  // from https://groups.google.com/d/msg/akka-user/9s4Yl7aEz3E/zfxmdc0cGQAJ
  "-J-XX:+UseG1GC",
  "-J-XX:+UseNUMA",
  "-J-XX:+AlwaysPreTouch",

  // probably can't use these with jstack and others tools
  "-J-XX:+PerfDisableSharedMem",
  "-J-XX:+ParallelRefProcEnabled",
  "-J-XX:+UseStringDeduplication")