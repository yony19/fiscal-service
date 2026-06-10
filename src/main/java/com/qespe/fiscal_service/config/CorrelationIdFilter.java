package com.qespe.fiscal_service.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Propagacion de un id de correlacion por request (E2.x observabilidad).
 *
 * <p><strong>Por que existe:</strong> en un sistema de microservicios sin un id que
 * viaje con la peticion, los logs de auth / product / sales / stock son islas: ante
 * un error de un cajero no hay forma de seguir UNA venta a traves de los servicios.
 * Este filtro toma el header {@code X-Correlation-Id} (si el gateway u otro servicio
 * ya lo envio) o genera uno nuevo, lo deja en el MDC para que TODA linea de log de
 * este request lo lleve, y lo devuelve en la respuesta para que el cliente/gateway
 * pueda correlacionar.
 *
 * <p>Corre con {@link Ordered#HIGHEST_PRECEDENCE} para que incluso los logs de la
 * cadena de seguridad lleven el id. El MDC se limpia SIEMPRE en el finally: los hilos
 * del pool se reusan y un MDC sucio filtraria el id de un request al siguiente.
 *
 * <p>Replica de la reference implementation de auth-service. El filtro no depende de
 * nada propio de auth: solo lee/genera el header, lo sanea y lo expone en MDC/respuesta.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private static final int MAX_LENGTH = 64;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String incoming = request.getHeader(HEADER);
        String correlationId = (incoming == null || incoming.isBlank())
                ? UUID.randomUUID().toString()
                : sanitize(incoming);

        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /**
     * Un header de correlacion es metadata de trazas controlada por el cliente, asi que
     * se sanea antes de tocar el MDC o la respuesta: se acota el largo y se permiten solo
     * caracteres seguros. Esto evita log/response-splitting (CRLF) y entradas de MDC
     * gigantes a partir de un header malicioso.
     */
    private static String sanitize(String value) {
        String trimmed = value.trim();
        if (trimmed.length() > MAX_LENGTH) {
            trimmed = trimmed.substring(0, MAX_LENGTH);
        }
        return trimmed.replaceAll("[^A-Za-z0-9_.\\-]", "_");
    }
}
