plugins {
  id("xroad.java-conventions")
}

dependencies {
  // SPIKE: this module is currently self-contained so the whole DSL + provider
  // can be reviewed in one place. On real implementation the base DSL types
  // (XRoadConfig, Scope, ConfigKey, Value, Source, Country, Validator,
  // ConfigKeyProvider) move to :lib:properties-core and this module keeps only
  // the *ConfigKeys providers, depending on properties-core for the DSL:
  // implementation(project(":lib:properties-core"))
}
