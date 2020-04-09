package org.niis.xroad.restapi.openapi;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.openapi.model.MemberName;
import org.niis.xroad.restapi.service.GlobalConfService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Member names controller for finding member names
 */
@Controller
@RequestMapping("/api")
@Slf4j
@PreAuthorize("denyAll")
public class MemberNamesApiController implements MemberNamesApi {
    private final GlobalConfService globalConfService;

    @Autowired
    public MemberNamesApiController(GlobalConfService globalConfService) {
        this.globalConfService = globalConfService;
    }

    @Override
    @PreAuthorize("hasAuthority('INIT_CONFIG')")
    public ResponseEntity<MemberName> findMemberName(String memberClass, String memberCode) {
        String memberName = globalConfService.findMemberName(memberClass, memberCode);
        if (StringUtils.isEmpty(memberName)) {
            throw new ResourceNotFoundException("member name not found");
        }
        return new ResponseEntity<>(new MemberName().memberName(memberName), HttpStatus.OK);
    }
}
