/**
 * The MIT License
 * Copyright (c) 2016 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
package ee.ria.xroad.opmonitordaemon;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;

/**
 * Agent that fixes a bug in Dropwizard metrics-core 3.1.0.
 * The JmxHistogram should return the metrics snapshot size
 * not the total count in the metrics object.
 */
public final class DropwizardBugfixAgent {

    private static final String BUGGY_CLASS =
            "com/codahale/metrics/JmxReporter$JmxHistogram";

    private static final String CORRECTED_RETURN_STATEMENT =
            "return (long) metric.getSnapshot().size();";

    private DropwizardBugfixAgent() {
    }

    /**
     * Agent method that is run before the main method.
     * @param args arguments
     * @param instrumentation java instrumentation
     */
    public static void premain(String args, Instrumentation instrumentation) {
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className,
                    Class<?> classBeingRedefined,
                    ProtectionDomain protectionDomain, byte[] classfileBuffer)
                            throws IllegalClassFormatException {
                return className.equals(BUGGY_CLASS) ? getFixedClassfile()
                        : classfileBuffer;
            }
        });
    }

    private static byte[] getFixedClassfile() {
        byte[] classfileBytes;
        try {
            ClassPool cp = ClassPool.getDefault();
            CtClass cc = cp.get(BUGGY_CLASS.replace('/', '.'));
            CtMethod m = cc.getDeclaredMethod("getCount");
            m.setBody(CORRECTED_RETURN_STATEMENT);

            classfileBytes = cc.toBytecode();
            cc.detach();
        } catch (Throwable t) { // We want to catch serious errors as well
            throw new RuntimeException("Failed to modify the class", t);
        }
        return classfileBytes;
    }
}
