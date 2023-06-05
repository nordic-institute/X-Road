/**
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.restapi.config;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.GenericFilterBean;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Filter which prevents session refresh for notifications endpoints
 * at NOTIFICATIONS_API_V1_PATH.
 * For these endpoints, requests do not affect session expiration -
 * so the HttpSession will expire even though these endpoints are polled
 * constantly.
 * This is achieved by manually updating session's setMaxInactiveInterval
 * to the value it would have if the endpoint was not called (and
 * resetting back to original value when other urls are accessed).
 */
@Slf4j
@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE)
public class NotificationApiDisableSessionTimeoutFilter extends GenericFilterBean {

    private static final String ORIGINAL_MAX_INACTIVE_ATTR =
            "originalMaxInactiveInterval";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpSession session = httpRequest.getSession(false);

        if (session == null) {
            // no need to alter session timeout
            chain.doFilter(request, response);
            return;
        }

        synchronized (session.getId().intern()) {
            if (session.getMaxInactiveInterval() <= 0) {
                chain.doFilter(request, response);
                return;
            }

            if (httpRequest.getServletPath()
                    .startsWith(ControllerUtil.NOTIFICATIONS_API_V1_PATH)) {
                // subtract session timeout when notifications api is called
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
                // reset session timeout to original value, when other urls are accessed
                Object maxInactive = session.getAttribute(ORIGINAL_MAX_INACTIVE_ATTR);

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
}
