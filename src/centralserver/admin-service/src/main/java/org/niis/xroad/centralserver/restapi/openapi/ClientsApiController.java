package org.niis.xroad.centralserver.restapi.openapi;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.centralserver.openapi.ClientsApi;
import org.niis.xroad.centralserver.openapi.model.Client;
import org.niis.xroad.centralserver.openapi.model.ClientId;
import org.niis.xroad.centralserver.openapi.model.ClientType;
import org.niis.xroad.centralserver.openapi.model.MemberName;
import org.niis.xroad.centralserver.openapi.model.PagedClients;
import org.niis.xroad.centralserver.openapi.model.PagingMetadata;
import org.niis.xroad.centralserver.openapi.model.PagingSortingParameters;
import org.niis.xroad.centralserver.restapi.entity.FlattenedSecurityServerClient;
import org.niis.xroad.centralserver.restapi.service.ClientSearchService;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.stream.Collectors;

import static java.lang.Math.toIntExact;

@Controller
@RequestMapping(ControllerUtil.API_V1_PREFIX)
@PreAuthorize("denyAll")
@RequiredArgsConstructor
public class ClientsApiController implements ClientsApi {

    private final ClientSearchService clientSearchService;

    @Override
    public ResponseEntity<Client> addClient(Client client) {
        return null;
    }

    @Override
    public ResponseEntity<Void> deleteClient(String id) {
        return null;
    }

    @Override
    @PreAuthorize("permitAll")
    public ResponseEntity<PagedClients> findClients(String q,
            PagingSortingParameters pagingSorting, String name,
            String instance, String memberClass,
            String memberCode, String subsystemCode,
            ClientType clientType, String securityServer) {
        PageRequest pageRequest = covertToPageRequest(pagingSorting);
        Page<FlattenedSecurityServerClient> page = clientSearchService.find(q, pageRequest);
        PagedClients pagedResults = convertToPagedClients(page);
        return ResponseEntity.ok(pagedResults);
    }

    private PagedClients convertToPagedClients(Page<FlattenedSecurityServerClient> page) {
        PagingMetadata meta = convertToMetadata(page);
        List<Client> clients = page.get().map(this::convertToClient).collect(Collectors.toList());
        PagedClients result = new PagedClients();
        result.setClients(clients);
        result.setPagingMetadata(meta);
        return result;
    }

    private Client convertToClient(FlattenedSecurityServerClient flattened) {
        Client client = new Client();
        switch (flattened.getType()) {
            case "Subsystem":
                client.setClientType(ClientType.SUBSYSTEM);
                break;
            case "XRoadMember":
                client.setClientType(ClientType.MEMBER);
                break;
            default:
                throw new IllegalStateException("unknown type " + flattened.getType());
        }
        client.setId(String.valueOf(flattened.getId()));
        client.setMemberName(flattened.getMemberName());
        client.setCreatedAt(null); // TO DO
        client.setUpdatedAt(null); // TO DO
        ClientId clientId = new ClientId();
        clientId.setInstanceId(flattened.getXroadInstance());
        clientId.setMemberClass(flattened.getMemberClass().getCode());
        clientId.setMemberCode(flattened.getMemberCode());
        clientId.setSubsystemCode(flattened.getSubsystemCode());
        client.setXroadId(clientId);
        return client;
    }

    private PagingMetadata convertToMetadata(Page page) {
        PagingMetadata meta = new PagingMetadata();
        meta.setTotalItems(toIntExact(page.getTotalElements()));
        return meta;
    }

    private PageRequest covertToPageRequest(PagingSortingParameters pagingSorting) {
        return PageRequest.of(
                pagingSorting.getOffset(),
                pagingSorting.getLimit(),
                Sort.by(pagingSorting.getSort()));
    }

    @Override
    public ResponseEntity<Client> getClient(String id) {
        return null;
    }

    @Override
    public ResponseEntity<Client> updateMemberName(String id, MemberName memberName) {
        return null;
    }
}
