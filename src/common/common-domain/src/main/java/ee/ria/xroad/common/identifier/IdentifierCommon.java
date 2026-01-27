/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
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
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 */
package ee.ria.xroad.common.identifier;

/**
 * Common interface for XRoad identifier types across different schema versions.
 * This interface decouples the business logic from the schema implementation,
 * allowing both v4 (legacy) and v5 (new) generated classes to be used interchangeably.
 * 
 * <p>Uses the NEW (v5) terminology as the canonical naming convention.</p>
 * 
 * <p>Terminology mapping:</p>
 * <ul>
 *   <li>v4: xRoadInstance, memberClass, memberCode, serverCode</li>
 *   <li>v5: dataspaceInstance, participantClass, participantCode, connectorCode</li>
 * </ul>
 * 
 * <p>This is a marker interface. See {@link IdentifierAdapter} for the adapter that
 * transforms generated classes to use unified naming.</p>
 */
public interface IdentifierCommon {
    // Marker interface - actual adaptation done via IdentifierAdapter
}
