package org.niis.xroad.restapi.config;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.io.IOException;

/**
 * Thrown when {@link LimitRequestSizesConfiguration} detects that request was too large.
 * Usually wrapped in another exception (HttpMessageNotReadableException), which
 * means ResponseStatus annotation does not translate to actual response status.
 * Here anyway, in case it would be thrown unwrapped.
 */
@ResponseStatus(value = HttpStatus.PAYLOAD_TOO_LARGE)
public class SizeLimitExceededException extends IOException {
    public SizeLimitExceededException(String s) {
        super(s);
    }
}
