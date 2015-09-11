package ee.ria.xroad.commonui.jaas;

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

    @Override
    public void init(FilterConfig config) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String ctx = httpRequest.getContextPath();

        if (
                // The /public_system_status/ path is available without
                // authentication.
                !httpRequest.getRequestURI().startsWith(
                    ctx + "/public_system_status")
                // Skip authentication for CSS and other UI-related elements.
                && !httpRequest.getRequestURI().startsWith(ctx + "/stylesheets/")
                && !httpRequest.getRequestURI().equals(ctx + "/application/skin")
                && !httpRequest.getRequestURI().equals(ctx + "/favicon.ico")
                // Finally check authentication.
                && httpRequest.getUserPrincipal() == null
                && !httpRequest.authenticate(httpResponse)) {
            // response has been committed
            return;
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    }
}
