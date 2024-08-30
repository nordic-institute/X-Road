package ee.ria.xroad.common.signature;

import ee.ria.xroad.common.util.MessageFileNames;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.xml.security.signature.XMLSignatureInput;
import org.apache.xml.security.utils.resolver.ResourceResolverContext;
import org.apache.xml.security.utils.resolver.ResourceResolverSpi;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

@RequiredArgsConstructor
public class SignatureResourceResolver extends ResourceResolverSpi {

    @Getter
    private final List<MessagePart> messageParts;
    private final String hashChainResult;

    @Override
    public boolean engineCanResolveURI(ResourceResolverContext context) {
        return switch (context.attr.getValue()) {
            case MessageFileNames.MESSAGE, MessageFileNames.SIG_HASH_CHAIN_RESULT -> true;
            default -> isAttachment(context.attr.getValue());
        };
    }
    @Override
    public XMLSignatureInput engineResolveURI(ResourceResolverContext context) {
        switch (context.attr.getValue()) {
            case MessageFileNames.MESSAGE:
                MessagePart part = getPart(MessageFileNames.MESSAGE);

                if (part != null && part.getMessage() != null) {
                    return new XMLSignatureInput(part.getMessage());
                }

                break;
            case MessageFileNames.SIG_HASH_CHAIN_RESULT:
                return new XMLSignatureInput(is(hashChainResult));
            default: // do nothing
        }

        if (isAttachment(context.attr.getValue())) {
            MessagePart part = getPart(context.attr.getValue());

            if (part != null && part.getData() != null){
                return new XMLSignatureInput(Base64.getEncoder().encodeToString(part.getData()));
            }
        }
        return null;
    }

    private MessagePart getPart(String name) {
        return messageParts.stream()
                .filter(part -> part.getName().equals(name))
                .findFirst()
                .orElse(null);
    }

    private boolean isAttachment(String uri) {
        return uri.startsWith("/attachment");
    }

    private static InputStream is(String str) {
        return new ByteArrayInputStream(str.getBytes(StandardCharsets.UTF_8));
    }
}
