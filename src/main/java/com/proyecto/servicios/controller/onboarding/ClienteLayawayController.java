package com.proyecto.servicios.controller.onboarding;

import com.proyecto.servicios.dto.onboarding.LayawayClienteRequest;
import com.proyecto.servicios.dto.onboarding.LayawayClienteResponse;
import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.service.onboarding.ClienteLayawayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/layaway")
@RequiredArgsConstructor
@Tag(name = "Layaway Clientes", description = "Endpoint unificado para gestión de clientes mediante banderas de operación")
public class ClienteLayawayController {

    private final ClienteLayawayService clienteLayawayService;

    @PostMapping("/cliente")
    @Operation(
            summary = "Operación unificada de clientes (Bandera 1 = Insertar, Bandera 2 = Actualizar, Bandera 3 = Eliminar/Baja Lógica)",
            description = "Ejecuta el registro completo con cuenta bancaria automática (bandera 1), actualización de datos preservando CURP/RFC (bandera 2) o baja lógica (bandera 3)."
    )
    public ResponseEntity<GenericResponse<LayawayClienteResponse>> procesarCliente(
            @Valid @RequestBody LayawayClienteRequest request) {

        LayawayClienteResponse response = clienteLayawayService.procesarOperacion(request);
        HttpStatus status = (request.getBandera() != null && request.getBandera() == 1) ? HttpStatus.CREATED : HttpStatus.OK;

        return ResponseEntity.status(status).body(GenericResponse.success(response));
    }
}
