/**
 * The MIT License
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.restapi.converter;

import lombok.Getter;
import org.niis.xroad.restapi.openapi.model.StateChangeAction;
import org.niis.xroad.restapi.service.StateChangeActionEnum;

import java.util.Arrays;
import java.util.Optional;

/**
 * Mapping between {@link org.niis.xroad.restapi.openapi.model.StateChangeAction} in api (enum) and
 * model {@link org.niis.xroad.restapi.service.StateChangeActionEnum} status string
 */
@Getter
public enum StateChangeActionMapping {

    DELETE(StateChangeActionEnum.DELETE, StateChangeAction.DELETE),
    ACTIVATE(StateChangeActionEnum.ACTIVATE, StateChangeAction.ACTIVATE),
    DISABLE(StateChangeActionEnum.DISABLE, StateChangeAction.DISABLE),
    LOGIN(StateChangeActionEnum.TOKEN_ACTIVATE, StateChangeAction.LOGIN),
    LOGOUT(StateChangeActionEnum.TOKEN_DEACTIVATE, StateChangeAction.LOGOUT),
    REGISTER(StateChangeActionEnum.REGISTER, StateChangeAction.REGISTER),
    UNREGISTER(StateChangeActionEnum.UNREGISTER, StateChangeAction.UNREGISTER),
    IMPORT_FROM_TOKEN(StateChangeActionEnum.IMPORT_FROM_TOKEN, StateChangeAction.IMPORT_FROM_TOKEN),
    GENERATE_KEY(StateChangeActionEnum.GENERATE_KEY, StateChangeAction.GENERATE_KEY),
    EDIT_FRIENDLY_NAME(StateChangeActionEnum.EDIT_FRIENDLY_NAME, StateChangeAction.EDIT_FRIENDLY_NAME),
    GENERATE_AUTH_CSR(StateChangeActionEnum.GENERATE_AUTH_CSR, StateChangeAction.GENERATE_AUTH_CSR),
    GENERATE_SIGN_CSR(StateChangeActionEnum.GENERATE_SIGN_CSR, StateChangeAction.GENERATE_SIGN_CSR);

    private final StateChangeActionEnum stateChangeActionEnum;
    private final StateChangeAction stateChangeAction;

    StateChangeActionMapping(StateChangeActionEnum stateChangeActionEnum, StateChangeAction stateChangeAction) {
        this.stateChangeActionEnum = stateChangeActionEnum;
        this.stateChangeAction = stateChangeAction;
    }

    /**
     * Return matching {@link StateChangeActionEnum}, if any
     * @param stateChangeAction
     */
    public static Optional<StateChangeActionEnum> map(StateChangeAction stateChangeAction) {
        return getFor(stateChangeAction).map(StateChangeActionMapping::getStateChangeActionEnum);
    }

    /**
     * Return matching {@link StateChangeAction}, if any
     * @param stateChangeActionEnum
     */
    public static Optional<StateChangeAction> map(StateChangeActionEnum stateChangeActionEnum) {
        return getFor(stateChangeActionEnum).map(StateChangeActionMapping::getStateChangeAction);
    }

    /**
     * Return matching {@link StateChangeActionMapping}, if any
     * @param stateChangeActionEnum
     */
    public static Optional<StateChangeActionMapping> getFor(StateChangeActionEnum stateChangeActionEnum) {
        return Arrays.stream(values())
                .filter(mapping -> mapping.stateChangeActionEnum.equals(stateChangeActionEnum))
                .findFirst();
    }

    /**
     * Return matching {@link StateChangeActionMapping}, if any
     * @param stateChangeAction
     */
    public static Optional<StateChangeActionMapping> getFor(StateChangeAction stateChangeAction) {
        return Arrays.stream(values())
                .filter(mapping -> mapping.stateChangeAction.equals(stateChangeAction))
                .findFirst();
    }

}
