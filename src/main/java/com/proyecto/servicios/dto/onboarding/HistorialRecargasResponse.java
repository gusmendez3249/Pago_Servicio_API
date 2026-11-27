package com.proyecto.servicios.dto.onboarding;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistorialRecargasResponse {
    private List<RecargaSaldoResponse> recargas;
    private int pagina;
    private int totalPaginas;
    private long totalRecargas;
}
