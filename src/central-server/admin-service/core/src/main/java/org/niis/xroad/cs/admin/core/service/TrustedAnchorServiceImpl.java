package org.niis.xroad.cs.admin.core.service;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.cs.admin.api.domain.TrustedAnchor;
import org.niis.xroad.cs.admin.api.service.TrustedAnchorService;
import org.niis.xroad.cs.admin.core.entity.mapper.TrustedAnchorMapper;
import org.niis.xroad.cs.admin.core.repository.TrustedAnchorRepository;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

import java.util.List;

import static java.util.stream.Collectors.toList;

@Service
@RequiredArgsConstructor
@Transactional
class TrustedAnchorServiceImpl implements TrustedAnchorService {
    private final TrustedAnchorRepository trustedAnchorRepository;
    private final TrustedAnchorMapper trustedAnchorMapper;

    @Override
    public List<TrustedAnchor> findAll() {
        return trustedAnchorRepository.findAll().stream()
                .map(trustedAnchorMapper::toTarget)
                .collect(toList());
    }
}
