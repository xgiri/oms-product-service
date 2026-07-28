package com.giri.oms.common.correlation;

public final class CorrelationIdConstants {

    /** Header clients can send to propagate their own ID; we also echo it back on the response under this name. */
    public static final String HEADER_NAME = "X-Correlation-Id";

    /** MDC key — referenced from logging.pattern.console in application.properties as %X{correlationId}. */
    public static final String MDC_KEY = "correlationId";

    private CorrelationIdConstants() {
    }
}
