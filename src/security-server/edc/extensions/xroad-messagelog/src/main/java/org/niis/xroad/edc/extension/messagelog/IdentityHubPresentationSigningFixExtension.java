package org.niis.xroad.edc.extension.messagelog;

import org.eclipse.edc.core.transform.transformer.to.JsonValueToGenericTypeTransformer;
import org.eclipse.edc.iam.identitytrust.transform.to.JsonObjectToPresentationQueryTransformer;
import org.eclipse.edc.identityhub.api.v1.PresentationApiController;
import org.eclipse.edc.identityhub.api.validation.PresentationQueryValidator;
import org.eclipse.edc.identityhub.core.PresentationCreatorRegistryImpl;
import org.eclipse.edc.identityhub.core.VerifiablePresentationServiceImpl;
import org.eclipse.edc.identityhub.core.creators.JwtPresentationGenerator;
import org.eclipse.edc.identityhub.spi.KeyPairService;
import org.eclipse.edc.identityhub.spi.ParticipantContextService;
import org.eclipse.edc.identityhub.spi.generator.PresentationCreatorRegistry;
import org.eclipse.edc.identityhub.spi.generator.VerifiablePresentationService;
import org.eclipse.edc.identityhub.spi.model.IdentityHubConstants;
import org.eclipse.edc.identityhub.spi.resolution.CredentialQueryResolver;
import org.eclipse.edc.identityhub.spi.verification.AccessTokenVerifier;
import org.eclipse.edc.identitytrust.model.CredentialFormat;
import org.eclipse.edc.identitytrust.model.credentialservice.PresentationQueryMessage;
import org.eclipse.edc.identitytrust.verification.SignatureSuiteRegistry;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.iam.PublicKeyResolver;
import org.eclipse.edc.spi.security.PrivateKeyResolver;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.token.JwtGenerationService;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.validator.spi.JsonObjectValidatorRegistry;
import org.eclipse.edc.verifiablecredentials.linkeddata.LdpIssuer;
import org.eclipse.edc.web.jersey.jsonld.JerseyJsonLdInterceptor;
import org.eclipse.edc.web.jersey.jsonld.ObjectMapperProvider;
import org.eclipse.edc.web.spi.WebService;

import java.time.Clock;

import static org.eclipse.edc.identityhub.core.CoreServicesExtension.OWN_DID_PROPERTY;
import static org.eclipse.edc.identitytrust.VcConstants.IATP_CONTEXT_URL;
import static org.eclipse.edc.spi.CoreConstants.JSON_LD;
import static org.niis.xroad.edc.extension.messagelog.IdentityHubPresentationSigningFixExtension.NAME;


@Extension(NAME)
public class IdentityHubPresentationSigningFixExtension implements ServiceExtension {

    public static final String NAME = "Presentation API Extension";
    public static final String RESOLUTION_SCOPE = "resolution-scope";
    public static final String RESOLUTION_CONTEXT = "resolution";

    private final String defaultSuite = IdentityHubConstants.JWS_2020_SIGNATURE_SUITE;

    @Inject
    private TypeTransformerRegistry typeTransformer;
    @Inject
    private JsonObjectValidatorRegistry validatorRegistry;
    @Inject
    private WebService webService;
    @Inject
    private AccessTokenVerifier accessTokenVerifier;
    @Inject
    private CredentialQueryResolver credentialResolver;
    @Inject
    private JsonLd jsonLd;
    @Inject
    private TypeManager typeManager;
    @Inject
    private ParticipantContextService participantContextService;


    @Inject
    private KeyPairService keyPairService;
    @Inject
    private PrivateKeyResolver privateKeyResolver;
    @Inject
    private Clock clock;
    @Inject
    private SignatureSuiteRegistry signatureSuiteRegistry;
    private PresentationCreatorRegistryImpl presentationCreatorRegistry;

    @Override
    public String name() {
        return NAME;
    }


    @Override
    public void initialize(ServiceExtensionContext context) {
        // setup validator
        validatorRegistry.register(PresentationQueryMessage.PRESENTATION_QUERY_MESSAGE_TYPE_PROPERTY, new PresentationQueryValidator());


        var controller = new PresentationApiController(validatorRegistry, typeTransformer, credentialResolver,
                accessTokenVerifier, presentationGenerator(context), context.getMonitor(), participantContextService);

        var jsonLdMapper = typeManager.getMapper(JSON_LD);
        webService.registerResource(RESOLUTION_CONTEXT, new ObjectMapperProvider(jsonLdMapper));
        webService.registerResource(RESOLUTION_CONTEXT, new JerseyJsonLdInterceptor(jsonLd, jsonLdMapper, RESOLUTION_SCOPE));
        webService.registerResource(RESOLUTION_CONTEXT, controller);

        jsonLd.registerContext(IATP_CONTEXT_URL, RESOLUTION_SCOPE);

        // register transformer -- remove once registration is handled in EDC
        typeTransformer.register(new JsonObjectToPresentationQueryTransformer(jsonLdMapper));
        typeTransformer.register(new JsonValueToGenericTypeTransformer(jsonLdMapper));
    }

    public PresentationCreatorRegistry presentationCreatorRegistry(ServiceExtensionContext context) {
        if (presentationCreatorRegistry == null) {
            presentationCreatorRegistry = new PresentationCreatorRegistryImpl(keyPairService);
            presentationCreatorRegistry.addCreator(new JwtPresentationGenerator(privateKeyResolver, clock, getOwnDid(context), new JwtGenerationService()), CredentialFormat.JWT);

            var ldpIssuer = LdpIssuer.Builder.newInstance().jsonLd(jsonLd).monitor(context.getMonitor()).build();
            presentationCreatorRegistry.addCreator(new CustomLdpPresentationGenerator(privateKeyResolver, getOwnDid(context),
                            signatureSuiteRegistry, defaultSuite, ldpIssuer, typeManager.getMapper(JSON_LD)),
                    CredentialFormat.JSON_LD);
        }
        return presentationCreatorRegistry;
    }

    public VerifiablePresentationService presentationGenerator(ServiceExtensionContext context) {
        return new VerifiablePresentationServiceImpl(CredentialFormat.JSON_LD, presentationCreatorRegistry(context), context.getMonitor());
    }

    private String getOwnDid(ServiceExtensionContext context) {
        return context.getConfig().getString(OWN_DID_PROPERTY);
    }
}
