package ee.cyber.xroad.mediator.client;

import java.io.IOException;
import java.security.cert.X509Certificate;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.db.HibernateUtil;
import ee.cyber.sdsb.common.db.TransactionCallback;
import ee.cyber.sdsb.common.util.AsyncHttpSender;
import ee.cyber.sdsb.common.util.CryptoUtils;
import ee.cyber.sdsb.common.util.PerformanceLogger;
import ee.cyber.xroad.mediator.common.AbstractMediatorHandler;
import ee.cyber.xroad.mediator.common.HttpClientManager;
import ee.cyber.xroad.mediator.common.MediatorMessageProcessor;
import ee.cyber.xroad.mediator.util.HttpHeaders;

import static ee.cyber.sdsb.common.ErrorCodes.*;

class ClientMediatorHandler extends AbstractMediatorHandler {

    private static final Logger LOG =
            LoggerFactory.getLogger(ClientMediatorHandler.class);

    ClientMediatorHandler(HttpClientManager httpClientManager) {
        super(httpClientManager);
    }

    @Override
    public void handle(String target, Request baseRequest,
            final HttpServletRequest request,
            final HttpServletResponse response)
                    throws IOException, ServletException {
        long start = PerformanceLogger.log(LOG, "Received request from " +
                request.getRemoteAddr());
        LOG.info("Received request from {}", request.getRemoteAddr());

        try {
            final MediatorMessageProcessor processor;

            MetaRequestProcessor.MetaRequest metaRequest =
                    MetaRequestProcessor.getMetaRequest(request);
            if (metaRequest != null) {
                processor = new MetaRequestProcessor(metaRequest,
                        httpClientManager);
            } else {
                verifyRequestMethod(request);

                processor = new ClientMediatorMessageProcessor(target,
                        httpClientManager, getClientCert(request)) {
                    @Override
                    protected AsyncHttpSender createSender() {
                        AsyncHttpSender sender = super.createSender();
                        addClientHeaders(request, sender);
                        return sender;
                    }
                };
            }

            HibernateUtil.doInTransaction(new TransactionCallback<Object>() {
                @Override
                public Object call(Session session) throws Exception {
                    process(processor, request, response);
                    return null;
                }
            });
        } catch (CodedException.Fault fault) {
            LOG.info("Handler got fault", fault);

            sendErrorResponse(response, fault);
        } catch (Exception ex) {
            LOG.error("Request processing error", ex);

            sendErrorResponse(response,
                    translateWithPrefix(SERVER_CLIENTPROXY_X, ex));
        } finally {
            baseRequest.setHandled(true);

            PerformanceLogger.log(LOG, start, "Request handled");
        }
    }

    private static ClientCert getClientCert(HttpServletRequest request) {
        String certVerify = request.getHeader(HttpHeaders.X_SSL_CLIENT_VERIFY);
        String certBase64 = request.getHeader(HttpHeaders.X_SSL_CLIENT_CERT);

        X509Certificate cert = null;
        if (certBase64 != null && !certBase64.isEmpty()) {
            try {
                certBase64 = certBase64.replaceAll(
                        "-----(BEGIN|END) CERTIFICATE-----", "");
                cert = CryptoUtils.readCertificate(certBase64);
            } catch (Exception e) {
                throw new CodedException(X_INTERNAL_ERROR,
                        "Could not read client certificate from request: %s",
                        e.getMessage());
            }
        }

        return new ClientCert(cert, certVerify);
    }
}
