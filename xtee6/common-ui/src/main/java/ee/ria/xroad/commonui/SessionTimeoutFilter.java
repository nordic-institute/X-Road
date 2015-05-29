package ee.ria.xroad.commonui;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import lombok.extern.slf4j.Slf4j;

/**
 * Filter that helps the session to time out in case automatic polling
 * requests are sent by client.
 */
@Slf4j
public class SessionTimeoutFilter implements Filter {

    private static final String ALLOW_TIMEOUT_PARAM = "allowTimeout";

    private static final String ORIGINAL_MAX_INACTIVE_ATTR =
        "originalMaxInactiveInterval";

    @Override
    public void init(FilterConfig config) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
            FilterChain chain) throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpSession session = httpRequest.getSession(false);

        if (session == null || !"GET".equals(httpRequest.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        synchronized (session.getId().intern()) {
            if (session.getMaxInactiveInterval() <= 0) {
                chain.doFilter(request, response);
                return;
            }

            if (request.getParameter(ALLOW_TIMEOUT_PARAM) != null) {
                if (session.getAttribute(ORIGINAL_MAX_INACTIVE_ATTR) == null) {
                    session.setAttribute(ORIGINAL_MAX_INACTIVE_ATTR,
                        session.getMaxInactiveInterval());
                }

                int secondsSinceLastRequest = (int)
                    (TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()
                      - session.getLastAccessedTime()));

                // Make sure newMaxInactiveInterval is never <= 0, i.e. we
                // don't disable session timeout.
                int newMaxInactiveInterval = Math.max(
                    1, session.getMaxInactiveInterval() - secondsSinceLastRequest);

                session.setMaxInactiveInterval(newMaxInactiveInterval);

                log.debug("DECREASED maxInactiveInterval by {} to {}",
                          secondsSinceLastRequest, session.getMaxInactiveInterval());
            } else if (session.getAttribute(ORIGINAL_MAX_INACTIVE_ATTR) != null) {
                Object maxInactive =
                    session.getAttribute(ORIGINAL_MAX_INACTIVE_ATTR);

                // For some reason, Jetty restores Integer as Long
                // after passivating session.
                session.setMaxInactiveInterval(maxInactive instanceof Long
                    ? ((Long) maxInactive).intValue() : ((Integer) maxInactive));

                session.removeAttribute(ORIGINAL_MAX_INACTIVE_ATTR);

                log.debug("RESTORED maxInactiveInterval to {}",
                    session.getMaxInactiveInterval());
            }
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    }
}
