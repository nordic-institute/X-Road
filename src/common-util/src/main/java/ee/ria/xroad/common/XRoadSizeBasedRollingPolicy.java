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
 package ee.ria.xroad.common;

import ch.qos.logback.core.CoreConstants;
import ch.qos.logback.core.rolling.FixedWindowRollingPolicy;
import ch.qos.logback.core.rolling.RolloverFailure;
import ch.qos.logback.core.rolling.helper.*;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * This class is largely a copy of Logback's FixedWindowRollingPolicy.
 *<p>
 * Replaces '%i' with timestamp and index number (necessary if there already
 * exists log for the same second). For example: '2015-10-12_165611-004'
 *</p><p>
 * This class was a work-around for
 * http://jira.qos.ch/browse/LOGBACK-992<br>
 *</p>
 * <b>This class will be removed in a future release.</b>
 */
@Deprecated
public class XRoadSizeBasedRollingPolicy extends FixedWindowRollingPolicy {
    static final String FNP_NOT_SET = "The \"FileNamePattern\" property must "
            + "be set before using FixedWindowRollingPolicy. ";
    static final String PRUDENT_MODE_UNSUPPORTED = "See also http://logback."
            + "qos.ch/codes.html#tbr_fnp_prudent_unsupported";
    static final String SEE_PARENT_FN_NOT_SET = "Please refer to http://logbac"
            + "k.qos.ch/codes.html#fwrp_parentFileName_not_set";

    FileNamePattern fileNamePattern;
    FileNamePattern zipEntryFileNamePattern;

    RenameUtil util = new RenameUtil();
    Compressor compressor;

    @Override
    public void start() {
        util.setContext(this.context);

        if (fileNamePatternStr != null) {
            fileNamePattern = new FileNamePattern(fileNamePatternStr,
                    this.context);
            determineCompressionMode();
        } else {
            addError(FNP_NOT_SET);
            addError(CoreConstants.SEE_FNP_NOT_SET);
            throw new IllegalStateException(
                    FNP_NOT_SET + CoreConstants.SEE_FNP_NOT_SET);
        }

        if (isParentPrudent()) {
            addError("Prudent mode is not supported "
                    + "with FixedWindowRollingPolicy.");
            addError(PRUDENT_MODE_UNSUPPORTED);
            throw new IllegalStateException("Prudent mode is not supported.");
        }

        if (getParentsRawFileProperty() == null) {
            addError("The File name property must be set "
                    + "before using this rolling policy.");
            addError(SEE_PARENT_FN_NOT_SET);
            throw new IllegalStateException("The \"File\" option must be set.");
        }

        IntegerTokenConverter itc = fileNamePattern.getIntegerTokenConverter();

        if (itc == null) {
            throw new IllegalStateException(
                    "FileNamePattern [" + fileNamePattern.getPattern()
                            + "] does not contain a valid IntegerToken");
        }

        if (compressionMode == CompressionMode.ZIP) {
            String zipEntryFileNamePatternStr = transformFileNamePatternFromInt2Date(
                    fileNamePatternStr);
            zipEntryFileNamePattern = new FileNamePattern(
                    zipEntryFileNamePatternStr, context);
        }
        compressor = new Compressor(compressionMode);
        compressor.setContext(this.context);
        super.start();
    }

    private String transformFileNamePatternFromInt2Date(
            String fileNamePatternStr) {
        String slashified = FileFilterUtil.slashify(fileNamePatternStr);
        String stemOfFileNamePattern = FileFilterUtil
                .afterLastSlash(slashified);
        return stemOfFileNamePattern.replace("%i",
                "%d{" + ZIP_ENTRY_DATE_PATTERN + "}");
    }

    @Override
    public void rollover() throws RolloverFailure {
        switch (compressionMode) {
            case NONE:
                util.rename(getActiveFileName(), getNameOfCompressedFile());
                break;
            case GZ:
                compressor.compress(getActiveFileName(),
                        getNameOfCompressedFile(), null);
                break;
            case ZIP:
                compressor.compress(getActiveFileName(),
                        getNameOfCompressedFile(),
                        zipEntryFileNamePattern.convert(new Date()));
                break;
            default:
                break;
        }
    }

    private String getNameOfCompressedFile() {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd_HHmmss");
        String formattedDate = df.format(new Date());

        int index = 1;
        String result = getIndexedFilename(formattedDate, index);

        while (new File(result).exists()) {
            index++;
            result = getIndexedFilename(formattedDate, index);
        }

        return result;
    }

    private String getIndexedFilename(String formattedDate, int index) {
        String replacement = String.format("%s-%d", formattedDate, index);
        return fileNamePatternStr.replace("%i", replacement);
    }

    /**
     * Return the value of the parent's RawFile property.
     */
    @Override
    public String getActiveFileName() {
        return getParentsRawFileProperty();
    }
}
