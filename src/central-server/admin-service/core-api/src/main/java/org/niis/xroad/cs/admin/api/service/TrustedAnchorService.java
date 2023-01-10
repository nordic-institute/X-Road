package org.niis.xroad.cs.admin.api.service;

import org.niis.xroad.cs.admin.api.domain.TrustedAnchor;

import java.util.List;

public interface TrustedAnchorService {

    List<TrustedAnchor> findAll();
}
