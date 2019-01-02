package org.niis.xroad.authproto.auth;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;

public class Http401AuthenticationEntryPoint implements AuthenticationEntryPoint {
    private static final Log logger = LogFactory.getLog(Http401AuthenticationEntryPoint.class);

    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException arg2) throws IOException, ServletException {
        if (logger.isDebugEnabled()) {
            logger.debug("Pre-authenticated entry point called. Rejecting access");
        }
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
    }
}
