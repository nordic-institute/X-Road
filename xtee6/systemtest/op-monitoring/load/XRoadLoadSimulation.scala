// PoC load tests for operational monitoring.

// Expecting the system parameter client_security_server_url to be set.

// FIXME: measure the average round trip for a user-given time
// FIXME: Ramp up the number of users until the average request duration
// increases by 5 times.
// FIXME: measure the number of parallel connections at the moment when
// the previous target is reached.

package opmonitor.loadtesting;

import io.gatling.core.Predef._ 

class XRoadLoadSimulation extends Simulation {

  // Use a separate object for the setup so it can be replaced before the build.
  setUp(SimulationSetup.Scenarios)
    .assertions(
      global.successfulRequests.percent.is(100)
  )
}
