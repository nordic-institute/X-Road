plugins {
  id("java-library")
}

dependencies {
  api(project(":common:common-globalconf"))
  api(project(":configuration-client:confclient-rpc-client"))

  testImplementation(project(":common:common-test"))
}

