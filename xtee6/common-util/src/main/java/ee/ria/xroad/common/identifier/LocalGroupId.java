/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
package ee.ria.xroad.common.identifier;

import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

/**
 * Local group ID.
 */
@XmlJavaTypeAdapter(IdentifierTypeConverter.LocalGroupIdAdapter.class)
public final class LocalGroupId extends AbstractGroupId {

    LocalGroupId() { // required by Hibernate
        this(null);
    }

    private LocalGroupId(String groupCode) {
        super(XroadObjectType.LOCALGROUP, null, groupCode);
    }

    /**
     * Factory method for creating a new LocalGroupId.
     * @param groupCode code of the new group
     * @return LocalGroupId
     */
    public static LocalGroupId create(String groupCode) {
        validateField("groupCode", groupCode);
        return new LocalGroupId(groupCode);
    }
}
