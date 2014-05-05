package ee.cyber.sdsb.commonui.jaas;

import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * A filter to force authentication by container. Uses
 * HttpServletRequest.authenticate() method introduced in Servlet 3.0
 * API. By requesting authentication programmatically we avoid
 * hard-coding user roles in web.xml <auth-constraint> element.
 */
public class AuthFilter implements Filter {

    public void init(FilterConfig config) throws ServletException {
    }

    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String ctx = httpRequest.getContextPath();

        // skip authentication for /stylesheets/* and favicon.ico
        if (!httpRequest.getRequestURI().startsWith(ctx + "/stylesheets/") &&
            !httpRequest.getRequestURI().equals(ctx + "/favicon.ico") &&
             httpRequest.getUserPrincipal() == null &&
            !httpRequest.authenticate(httpResponse)) {
            // response has been committed
            return;
        }

        chain.doFilter(request, response);
    }

    public void destroy() {
    }
}
