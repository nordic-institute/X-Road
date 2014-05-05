package ee.cyber.sdsb.signer;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import ee.cyber.sdsb.signer.testcases.TestSimpleRequests;

@RunWith(Suite.class)
@Suite.SuiteClasses({
    SignerHelper.Start.class,
    TestSimpleRequests.class,
    //SimpleScenario.class, // TODO: FIx certHash -> certId
    //DisableAndActivateBetweenKeys.class, // XXX: Will fail, if run together with other tests
    //UnexpectedHappenings.class, // TODO: Make sure it works with new Signer
    //MemberSubsystem.class, // TODO: Make sure it works with new Signer
    //TestIdentificators.class, // TODO: Make sure it works with new Signer
    SignerHelper.Stop.class
})

public class SignerTestSuite {
    // the class remains empty,
    // used only as a holder for the above annotations
}
