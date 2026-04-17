plugins {
  id("xroad.java-conventions")
}

dependencies {
  implementation(libs.edc.sql.contract.negotiation)
  implementation(libs.edc.sql.transfer.process)
  implementation(libs.edc.sql.edr.index)
  implementation(libs.edc.sql.dataplane.instance)
  implementation(libs.edc.sql.jti.validation)
  implementation(libs.edc.sql.policy.monitor)
  implementation(libs.edc.sql.fedcatalog.cache)
  implementation(libs.edc.sql.fedcatalog.target.node)

  // SQL infrastructure
  implementation(libs.edc.sql.core)
  implementation(libs.edc.sql.lease)
  implementation(libs.edc.sql.lease.core)
  implementation(libs.edc.sql.pool)
  implementation(libs.edc.transaction.local)

  // Virtual Connector extras
  implementation(libs.edcv.cel.store.sql)
  implementation(libs.edcv.tasks.store.sql)
  implementation(libs.edcv.participantcontext.store.sql)
  implementation(libs.edc.store.participantcontext.config.sql)
}

archUnit {
  setSkip(true)
}
