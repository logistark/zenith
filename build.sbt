lazy val cats_ver = "0.6.1"
lazy val nscala_time_ver = "1.6.0"
lazy val circe_ver = "0.5.0-M2"
lazy val zenith_ver = "0.4.0"

lazy val cats = "org.typelevel" %% "cats" % cats_ver
lazy val nscala_time = "com.github.nscala-time" %% "nscala-time" % nscala_time_ver
lazy val circe_core = "io.circe" %% "circe-core" % circe_ver
lazy val circe_generic = "io.circe" %% "circe-generic" % circe_ver
lazy val circe_jawn = "io.circe" %% "circe-jawn" % circe_ver
lazy val zenith = "io.github.sungiant" %% "zenith" % zenith_ver
lazy val zenith_netty = "io.github.sungiant" %% "zenith-netty" % zenith_ver
lazy val zenith_default = "io.github.sungiant" %% "zenith-default" % zenith_ver

lazy val sonatype = "Sonatype" at "https://oss.sonatype.org/content/repositories/releases/"
lazy val typesafe = "Typesafe" at "http://repo.typesafe.com/typesafe/releases/"

lazy val commonSettings =
  (scalaVersion := "2.11.8") ::
  (resolvers += sonatype) ::
  (resolvers += typesafe) ::
  (libraryDependencies += cats) ::
  (libraryDependencies += nscala_time) ::
  (libraryDependencies += circe_core) ::
  (libraryDependencies += circe_generic) ::
  (libraryDependencies += circe_jawn) ::
  (libraryDependencies += zenith) ::
  (libraryDependencies += zenith_netty) ::
  (libraryDependencies += zenith_default) ::
  (scalaSource in Compile := baseDirectory.value) ::
  (connectInput in run := true) ::
  (fork in run := true) ::
  (parallelExecution in ThisBuild := false) :: Nil

lazy val demo_server = project
  .in (file ("demo.server"))
  .settings (moduleName := "demo-server")
  .settings (commonSettings: _*)
  .settings (unmanagedResourceDirectories in Compile += baseDirectory.value)

lazy val demo_bot = project
  .in (file ("demo.bot"))
  .settings (moduleName := "demo-bot")
  .settings (commonSettings: _*)

lazy val root = project
  .in (file ("."))
  .settings (commonSettings: _*)
  .aggregate (demo_server, demo_bot)
