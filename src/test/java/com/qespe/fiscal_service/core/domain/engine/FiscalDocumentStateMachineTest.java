package com.qespe.fiscal_service.core.domain.engine;

import com.qespe.fiscal_service.core.domain.enums.FiscalDocumentStatus;
import com.qespe.fiscal_service.shared.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.qespe.fiscal_service.core.domain.enums.FiscalDocumentStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests de la maquina de estados fiscal (E1.x). Es la guarda que impide
 * corromper el ciclo de vida de un comprobante (p. ej. re-firmar un documento
 * ya ACCEPTED, o saltar de RESERVED a SENT sin XML ni firma). Una transicion
 * invalida aqui significa un comprobante legalmente inconsistente.
 */
class FiscalDocumentStateMachineTest {

    @Test
    @DisplayName("Camino feliz sincrono: RESERVED -> PENDING_XML -> XML_GENERATED -> SIGNED -> QUEUED_FOR_SEND -> SENT -> ACCEPTED")
    void happyPathSynchronous() {
        assertThatCode(() -> {
            FiscalDocumentStateMachine.assertTransition(RESERVED, PENDING_XML);
            FiscalDocumentStateMachine.assertTransition(PENDING_XML, XML_GENERATED);
            FiscalDocumentStateMachine.assertTransition(XML_GENERATED, SIGNED);
            FiscalDocumentStateMachine.assertTransition(SIGNED, QUEUED_FOR_SEND);
            FiscalDocumentStateMachine.assertTransition(QUEUED_FOR_SEND, SENT);
            FiscalDocumentStateMachine.assertTransition(SENT, ACCEPTED);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Camino async (RA/RC): SENT -> TICKETED -> ACCEPTED")
    void asyncTicketPath() {
        assertThatCode(() -> {
            FiscalDocumentStateMachine.assertTransition(SENT, TICKETED);
            FiscalDocumentStateMachine.assertTransition(TICKETED, ACCEPTED);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("SENT y TICKETED tambien pueden terminar en REJECTED u OBSERVED")
    void rejectionPaths() {
        assertThatCode(() -> {
            FiscalDocumentStateMachine.assertTransition(SENT, REJECTED);
            FiscalDocumentStateMachine.assertTransition(SENT, OBSERVED);
            FiscalDocumentStateMachine.assertTransition(TICKETED, REJECTED);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Saltarse etapas esta prohibido: RESERVED -> SENT y XML_GENERATED -> ACCEPTED")
    void skippingStagesThrows() {
        assertThatThrownBy(() -> FiscalDocumentStateMachine.assertTransition(RESERVED, SENT))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> FiscalDocumentStateMachine.assertTransition(XML_GENERATED, ACCEPTED))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Un estado terminal no transiciona: ACCEPTED -> SIGNED prohibido")
    void terminalStateDoesNotTransition() {
        assertThatThrownBy(() -> FiscalDocumentStateMachine.assertTransition(ACCEPTED, SIGNED))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Reemision tras rechazo: REJECTED -> PENDING_XML permitido; REJECTED -> SIGNED/SENT prohibido")
    void rejectedCanOnlyReissueFromScratch() {
        assertThatCode(() -> FiscalDocumentStateMachine.assertTransition(REJECTED, PENDING_XML))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> FiscalDocumentStateMachine.assertTransition(REJECTED, SIGNED))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> FiscalDocumentStateMachine.assertTransition(REJECTED, SENT))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("isTerminalOrAdvanced: ACCEPTED/REJECTED/VOIDED true; SENT/RESERVED false")
    void terminalDetection() {
        assertThat(FiscalDocumentStateMachine.isTerminalOrAdvanced(ACCEPTED)).isTrue();
        assertThat(FiscalDocumentStateMachine.isTerminalOrAdvanced(REJECTED)).isTrue();
        assertThat(FiscalDocumentStateMachine.isTerminalOrAdvanced(VOIDED)).isTrue();
        assertThat(FiscalDocumentStateMachine.isTerminalOrAdvanced(SENT)).isFalse();
        assertThat(FiscalDocumentStateMachine.isTerminalOrAdvanced(RESERVED)).isFalse();
    }
}
