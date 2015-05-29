package ee.ria.xroad_legacy.logreader;

import org.apache.wicket.Page;
import org.apache.wicket.protocol.http.WebApplication;

public class LogReaderUIApplication extends WebApplication {

    @Override
    public Class<? extends Page> getHomePage() {
        return LogReaderUI.class;
    }

    @Override
    protected void init() {
        super.init();
    }

}
