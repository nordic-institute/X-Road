package ee.cyber.sdsb.signer.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ee.cyber.sdsb.signer.core.TokenManager.TokenUpdateCallback;
import ee.cyber.sdsb.signer.core.device.DeviceType;
import ee.cyber.sdsb.signer.core.device.DeviceTypeConf;
import ee.cyber.sdsb.signer.core.device.TokenType;
import ee.cyber.sdsb.signer.core.model.Token;

class TokenManagerHelper {

    static void syncTokens(List<Token> tokens,
            TokenUpdateCallback updateCallback) {
        List<TokenType> tokensFromDevices = new ArrayList<>();

        // Collect all tokens from all devices
        for (DeviceType deviceType : DeviceTypeConf.getDevices()) {
            List<TokenType> tokensFromDevice = listTokens(deviceType);
            tokensFromDevices.addAll(tokensFromDevice);
        }

        // Add new tokens from devices
        addNewTokens(tokens, tokensFromDevices, updateCallback);

        // Cleanup lost tokens (tokens that are not on devices anymore)
        cleanupLostTokens(tokensFromDevices, updateCallback);
    }

    private static void addNewTokens(List<Token> tokens,
            List<TokenType> tokensFromDevices,
            TokenUpdateCallback updateCallback) {
        for (TokenType tokenType : tokensFromDevices) {
            Token token = getToken(tokenType);
            if (token != null) {
                addTokenIfUsable(token, tokens, updateCallback);
                continue;
            }

            if (TokenManager.CONF != null) {
                token = TokenManager.CONF.getToken(tokenType);
            }

            if (token == null) {
                token = new Token(tokenType.getDeviceType(), tokenType.getId());
                token.setReadOnly(tokenType.isReadOnly());
                token.setSerialNumber(tokenType.getSerialNumber());
                token.setLabel(tokenType.getLabel());
                token.setSlotIndex(tokenType.getSlotIndex());
                token.setFriendlyName(getDefaultFriendlyName(tokenType));
                token.setBatchSigningEnabled(tokenType.isBatchSigningEnabled());
            }

            token.setAvailable(true);

            if (!DeviceTypeConf.hasDevice(token.getType())) {
                token.setAvailable(false);
                token.setActive(false);
            }

            TokenManager.LOG.debug("Adding new token '{}#{}'",
                    tokenType.getDeviceType(), token.getId());
            tokens.add(token);

            updateCallback.tokenAdded(token.toDTO(), tokenType);
        }
    }

    private static void cleanupLostTokens(List<TokenType> tokensFromDevices,
            TokenUpdateCallback updateCallback) {
        for (Token token : TokenManager.currentTokens) {
            boolean found = false;
            for (TokenType tokenType : tokensFromDevices) {
                if (token.matches(tokenType)) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                TokenManager.LOG.warn("Lost token {}#{}", token.getType(),
                        token.getId());

                updateCallback.tokenRemoved(token.getId());
            }
        }
    }

    private static void addTokenIfUsable(Token token, List<Token> tokens,
            TokenUpdateCallback updateCallback) {
        boolean inTokenConf = TokenManager.CONF != null ?
                TokenManager.CONF.hasToken(token.getId()) : false;
        boolean inDeviceTypeConf = DeviceTypeConf.hasDevice(token.getType());
        if (inTokenConf || inDeviceTypeConf) {
            TokenManager.LOG.debug("Keeping token {}#{}", token.getType(),
                    token.getId());

            token.setAvailable(inDeviceTypeConf);

            tokens.add(token);
        } else {
            TokenManager.LOG.warn("Lost token {}#{}", token.getType(),
                    token.getId());

            updateCallback.tokenRemoved(token.getId());
        }
    }

    private static List<TokenType> listTokens(DeviceType deviceType) {
        try {
            return deviceType.listTokens();
        } catch (Exception e) {
            TokenManager.LOG.error("Failed to list tokens for device "
                    + deviceType.getType(), e);
            return Collections.emptyList();
        }
    }

    private static Token getToken(TokenType tokenType) {
        for (Token token : TokenManager.currentTokens) {
            if (token.matches(tokenType)) {
                return token;
            }
        }

        return null;
    }

    // TODO: How to assemble the default friendly name?
    private static String getDefaultFriendlyName(TokenType tokenType) {
        String name = tokenType.getDeviceType();

        if (tokenType.getSerialNumber() != null) {
            name += "-" + tokenType.getSerialNumber();
        }

        if (tokenType.getLabel() != null) {
            name += "-" + tokenType.getLabel();
        }

        if (tokenType.getSlotIndex() != null) {
            name += "-" + tokenType.getSlotIndex();
        }

        return name;
    }

}
