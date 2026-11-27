package com.proyecto.servicios.service.onboarding;

import com.proyecto.servicios.dto.onboarding.CuentaClienteResponse;
import com.proyecto.servicios.enums.MotivoPago;
import com.proyecto.servicios.exception.AccesoDenegadoException;
import com.proyecto.servicios.exception.ClienteInactivoException;
import com.proyecto.servicios.mapper.PagoServicioMapper;
import com.proyecto.servicios.repositorys.onboarding.ClienteRepository;
import com.proyecto.servicios.repositorys.onboarding.CuentaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** Cuentas del cliente autenticado (las que puede usar para pagar). */
@Service
@RequiredArgsConstructor
public class CuentaClienteService {

    private static final String ESTATUS_ACTIVA = "ACTIVA";

    private final SesionService sesionService;
    private final CuentaRepository cuentaRepository;
    private final ClienteRepository clienteRepository;

    /**
     * Solo las cuentas ACTIVAS del titular de la sesión (puede ser una lista vacía).
     * @throws AccesoDenegadoException     403 NO_TITULAR si la sesión es de un ejecutivo
     * @throws ClienteInactivoException    403 CLIENTE_INACTIVO si el cliente fue dado de baja
     */
    public List<CuentaClienteResponse> listarMias(String authorization) {
        SesionUsuario sesion = sesionService.autenticar(authorization);
        if (sesion.esEjecutivo() || sesion.clienteId() == null) {
            throw new AccesoDenegadoException("Solo el titular de la cuenta puede consultar sus cuentas.", MotivoPago.NO_TITULAR);
        }
        boolean activo = clienteRepository.findById(sesion.clienteId())
                .map(cliente -> Boolean.TRUE.equals(cliente.getActivo()))
                .orElse(false);
        if (!activo) {
            throw new ClienteInactivoException("El cliente se encuentra inactivo.", MotivoPago.CLIENTE_INACTIVO);
        }
        return cuentaRepository.findByClienteIdAndEstatusOrderByIdAsc(sesion.clienteId(), ESTATUS_ACTIVA).stream()
                .map(cuenta -> CuentaClienteResponse.builder()
                        .numCuenta(cuenta.getNumeroCuenta())
                        .numCuentaEnmascarada(PagoServicioMapper.enmascarar(cuenta.getNumeroCuenta()))
                        .saldo(cuenta.getSaldo())
                        .estatus(cuenta.getEstatus())
                        .build())
                .toList();
    }
}
