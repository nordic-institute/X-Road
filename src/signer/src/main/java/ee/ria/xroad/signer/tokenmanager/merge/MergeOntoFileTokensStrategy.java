/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
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
package ee.ria.xroad.signer.tokenmanager.merge;

import ee.ria.xroad.signer.model.Cert;
import ee.ria.xroad.signer.model.Key;
import ee.ria.xroad.signer.model.Token;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.stream.Collectors.partitioningBy;
import static java.util.stream.Collectors.toMap;

/**
 * A {@link TokenMergeStrategy} for merging an in-memory token list to tokens from a key conf xml file.
 * This strategy merges existing memory data onto new file based data.
 *
 */
@Slf4j
public class MergeOntoFileTokensStrategy implements TokenMergeStrategy {

    private List<Cert> newCertsFromFile = new ArrayList<>();

    // for testing
    protected List<Cert> getNewCertsFromFile() {
        return newCertsFromFile;
    }

    @Override
    public MergeResult merge(List<Token> fileTokens, List<Token> memoryTokens) {

        this.newCertsFromFile = new ArrayList<>();

        Map<String, Token> fileTokensMap = fileTokens.stream()
                .collect(toMap(Token::getId, Function.identity()));


        memoryTokens.forEach(
                // add any missing tokens from memory to file, match tokens based on token id
                memoryToken -> fileTokensMap.merge(memoryToken.getId(), memoryToken,
                        this::mergeToken));

        return new MergeResult(new ArrayList<>(fileTokensMap.values()), this.newCertsFromFile);
    }

    Token mergeToken(Token fileToken, Token memoryToken) {

        // copy any transient, needed data over to the file token
        // some of this info is loaded from a different file and some of these
        // have little use for a software token
        fileToken.setActive(memoryToken.isActive());
        fileToken.setAvailable(memoryToken.isAvailable());
        fileToken.setInfo(memoryToken.getTokenInfo());
        fileToken.setStatus(memoryToken.getStatus());
        fileToken.setModuleId(memoryToken.getModuleId());
        fileToken.setReadOnly(memoryToken.isReadOnly());

        mergeKeyLists(fileToken.getKeys(), memoryToken.getKeys());
        fileToken.setInfo(mergeTokenInfo(fileToken.getTokenInfo(), memoryToken.getTokenInfo()));

        return fileToken;
    }

    void mergeKeyLists(List<Key> fileKeyList, List<Key> memoryKeyList) {

        // prepare for the very unlikely (but unchecked?) case that key id is not unique, combine it with public key

        // NB: if key-id and public key are the same but different certificates are listed for them,
        // the "new added certificates" - detection will not work properly. this was seen as a fringe
        // case we con't care about enough to create handling for.
        Function<Key, String> keyMapperFunction = key -> String.join("-", key.getId(), key.getPublicKey());

        Map<String, Key> fileKeysMap = mapKeyListToMap(fileKeyList, keyMapperFunction);

        // will not add keys from memory to file keys: keys might have been removed in the file update
        memoryKeyList.stream()
                .filter(key -> fileKeysMap.containsKey(keyMapperFunction.apply(key)))
                .forEach(
                        memKey -> mergeKey(fileKeysMap.get(keyMapperFunction.apply(memKey)), memKey));

        fileKeyList.clear();
        fileKeyList.addAll(fileKeysMap.values());

        // need to keep a list of new, added certificates..
        Map<String, Key> memKeysMap = mapKeyListToMap(memoryKeyList, keyMapperFunction);

        // ..certs from new keys are definitely new certs, so add them
        fileKeysMap.entrySet().stream().map(Map.Entry::getKey).filter(keyString -> !memKeysMap.containsKey(keyString))
                .flatMap(key -> fileKeysMap.get(key).getCerts().stream())
                .forEach(newCertsFromFile::add);

    }

    private Map<String, Key> mapKeyListToMap(List<Key> keys, Function<Key, String> mapperFunction) {
        return keys.stream()
                .collect(toMap(mapperFunction, Function.identity(), (key, key2) -> {
                    log.error("Two keys with the same (id,public key) were in list. Will merge key: {} onto key: {}",
                            key2, key);
                    mergeKey(key, key2);
                    return key;
                }));
    }

    Map<String, String> mergeTokenInfo(Map<String, String> fileTokenInfo, Map<String, String> memoryTokenInfo) {
        return fileTokenInfo;
    }

    /** Merge key memoryKey onto fileKey
     * @param fileKey
     * @param memoryKey
     */
    void mergeKey(Key fileKey, Key memoryKey) {
        // rest of the fields come from the xml
        fileKey.setAvailable(memoryKey.isAvailable());

        // merge cert lists as certs have transient data
        mergeCertLists(fileKey.getCerts(), memoryKey.getCerts());

        // don't merge cert requests, no new information exists in memory about them, only old and possibly expired info
    }


    void mergeCertLists(List<Cert> fileCerts, List<Cert> memoryCerts) {
        // Don't add any certificates from memory list to file list. -- Certs may have been removed in the update.
        // If existing certs match though, merge their contents.

        Map<String, Cert> memoryCertMap = memoryCerts.stream()
                .collect(toMap(Cert::getId, Function.identity()));

        // Group file certs by earlier existence in memory:
        // 1) new certificates from file: -> list of certs needing OCSP responses
        Map<Boolean, List<Cert>> fileCertsGroupedByExistsInMemory =
                fileCerts.stream().collect(partitioningBy(cert -> memoryCertMap.containsKey(cert.getId())));

        this.newCertsFromFile.addAll(fileCertsGroupedByExistsInMemory.get(false));

        // 2) old certificates from file: -> merge file data with in-memory data
        fileCertsGroupedByExistsInMemory.get(true)
                .forEach(fileCert -> mergeCert(fileCert, memoryCertMap.get(fileCert.getId())));

    }

    void mergeCert(Cert fileCert, Cert memoryCert) {
        fileCert.setOcspResponse(memoryCert.getOcspResponse());
    }

}
