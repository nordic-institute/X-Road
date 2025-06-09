package org.niis.xroad.signer.jpa.service.impl;

import ee.ria.xroad.common.identifier.ClientId;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.serverconf.impl.ServerConfDatabaseCtx;
import org.niis.xroad.serverconf.impl.dao.IdentifierDAOImpl;
import org.niis.xroad.serverconf.impl.dao.SignerKeyCertRequestDaoImpl;
import org.niis.xroad.serverconf.impl.entity.SignerCertRequestEntity;
import org.niis.xroad.signer.core.service.TokenKeyCertRequestService;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class TokenKeyCertRequestServiceImpl implements TokenKeyCertRequestService {
    private final ServerConfDatabaseCtx serverConfDatabaseCtx;
    private final SignerKeyCertRequestDaoImpl keyCertRequestDao;
    private final IdentifierDAOImpl identifierDAO;

    @Override
    public Long save(Long keyId,
                     String externalId,
                     ClientId.Conf memberId,
                     String subjectName,
                     String subjectAltName,
                     String certificateProfile) throws Exception {
        return serverConfDatabaseCtx.doInTransaction(session -> {
            var certReq = new SignerCertRequestEntity();
            certReq.setKeyId(keyId);
            certReq.setExternalId(externalId);
            certReq.setMember(identifierDAO.findOrCreateClientId(session, memberId));
            certReq.setSubjectName(subjectName);
            certReq.setSubjectAlternativeName(subjectAltName);
            certReq.setCertificateProfile(certificateProfile);

            keyCertRequestDao.save(session, certReq);
            return certReq.getId();
        });
    }

    @Override
    public boolean delete(Long id) throws Exception {
        return serverConfDatabaseCtx.doInTransaction(session ->
                keyCertRequestDao.deleteById(session, SignerCertRequestEntity.class, id));
    }

}
