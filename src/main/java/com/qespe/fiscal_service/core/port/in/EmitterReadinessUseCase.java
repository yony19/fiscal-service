package com.qespe.fiscal_service.core.port.in;

import com.qespe.fiscal_service.core.dto.emitter.EmitterReadinessResponse;

import java.util.UUID;

/** Computes "are we ready to emit?" for a given emitter. */
public interface EmitterReadinessUseCase {
    EmitterReadinessResponse computeFor(UUID emitterId);
}
