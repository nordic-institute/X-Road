plugins {
  id("xroad.java-conventions")
}

dependencies {
  runtimeOnly(libs.edc.ih.sql.credentials)
  runtimeOnly(libs.edc.ih.sql.did)
  runtimeOnly(libs.edc.ih.sql.keypair)
  runtimeOnly(libs.edc.ih.sql.holder.credential.request)
  runtimeOnly(libs.edc.ih.sql.holder.credential.offer)
  runtimeOnly(libs.edc.ih.sql.sts.client)
  runtimeOnly(libs.edc.sql.core)
  runtimeOnly(libs.edc.transaction.local)
  runtimeOnly(libs.edc.sql.pool)
  runtimeOnly(libs.edc.core.sql.bootstrapper)
  runtimeOnly(libs.edc.sql.jti.validation)
  runtimeOnly(libs.edc.sql.lease.core)
  runtimeOnly(libs.edc.sql.participantcontext.store)
  runtimeOnly(libs.edc.store.participantcontext.config.sql)
  runtimeOnly(libs.postgresql)
}

archUnit {
  setSkip(true)
}
