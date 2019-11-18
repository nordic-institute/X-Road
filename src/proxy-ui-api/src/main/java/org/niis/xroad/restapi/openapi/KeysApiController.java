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
package org.niis.xroad.restapi.openapi;

import ee.ria.xroad.signer.protocol.dto.KeyInfo;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.converter.KeyConverter;
import org.niis.xroad.restapi.openapi.model.Key;
import org.niis.xroad.restapi.openapi.model.KeyName;
import org.niis.xroad.restapi.service.KeyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * keys controller
 */
@Controller
@RequestMapping("/api")
@Slf4j
@PreAuthorize("denyAll")
public class KeysApiController implements KeysApi {

    private final KeyService keyService;
    private final KeyConverter keyConverter;

    /**
     * KeysApiController constructor
     * @param keyConverter
     * @param keyService
     */

    @Autowired
    public KeysApiController(KeyService keyService,
            KeyConverter keyConverter) {
        this.keyService = keyService;
        this.keyConverter = keyConverter;
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_KEYS')")
    public ResponseEntity<Key> getKey(String keyId) {
        Key key = getKeyFromService(keyId);
        return new ResponseEntity<>(key, HttpStatus.OK);
    }

    private Key getKeyFromService(String keyId) {
        try {
            KeyInfo keyInfo = keyService.getKey(keyId);
            return keyConverter.convert(keyInfo);
        } catch (KeyService.KeyNotFoundException e) {
            throw new ResourceNotFoundException(e);
        }
    }

    @Override
    @PreAuthorize("hasAuthority('EDIT_KEYS')")
    public ResponseEntity<Key> updateKey(String id, KeyName keyName) {
        KeyInfo keyInfo = null;
        try {
            keyInfo = keyService.updateKeyFriendlyName(id, keyName.getName());
        } catch (KeyService.KeyNotFoundException e) {
            throw new ResourceNotFoundException(e);
        }
        Key key = keyConverter.convert(keyInfo);
        return new ResponseEntity<>(key, HttpStatus.OK);
    }

}
