package com.qespe.fiscal_service.config;

import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Propaga el id de correlacion hacia las llamadas HTTP salientes entre servicios.
 *
 * <p>Complementa a {@link CorrelationIdFilter}: el filtro deja el id en el MDC del hilo
 * del request entrante; este interceptor lo copia al header {@code X-Correlation-Id} de
 * cada request saliente (RestTemplate/RestClient) que sale en ese mismo hilo. Sin esto el
 * id se corta en cada salto interno que no pase por el gateway, rompiendo la traza E2E.
 *
 * <p>Es best-effort: si no hay id en el MDC (ej. una llamada desde un hilo @Async sin
 * propagacion de MDC) simplemente no agrega el header. No pisa un header ya presente.
 */
public class CorrelationIdClientInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null && !correlationId.isBlank()
                && !request.getHeaders().containsKey(CorrelationIdFilter.HEADER)) {
            request.getHeaders().set(CorrelationIdFilter.HEADER, correlationId);
        }
        return execution.execute(request, body);
    }
}
