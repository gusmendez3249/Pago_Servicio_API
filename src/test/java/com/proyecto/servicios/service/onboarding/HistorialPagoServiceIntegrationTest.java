package com.proyecto.servicios.service.onboarding;

import com.proyecto.servicios.config.CatalogoProperties;
import com.proyecto.servicios.dto.onboarding.DetalleErrorPago;
import com.proyecto.servicios.dto.onboarding.FiltrosHistorialDto;
import com.proyecto.servicios.dto.onboarding.HistorialPagosRequest;
import com.proyecto.servicios.dto.onboarding.HistorialPagosResponse;
import com.proyecto.servicios.dto.onboarding.PagoServicioResponse;
import com.proyecto.servicios.entity.onboarding.ClienteEntity;
import com.proyecto.servicios.entity.onboarding.CuentaEntity;
import com.proyecto.servicios.entity.onboarding.TransaccionPagoEntity;
import com.proyecto.servicios.entity.productos.ProductoPostgresEntity;
import com.proyecto.servicios.enums.EstatusTransaccionPago;
import com.proyecto.servicios.enums.MotivoPago;
import com.proyecto.servicios.exception.AccesoDenegadoException;
import com.proyecto.servicios.mapper.PagoServicioMapper;
import com.proyecto.servicios.repositorys.onboarding.ClienteRepository;
import com.proyecto.servicios.repositorys.onboarding.CuentaRepository;
import com.proyecto.servicios.repositorys.onboarding.TransaccionPagoRepository;
import com.proyecto.servicios.repositorys.productos.ProductoPostgresRepository;
import com.proyecto.servicios.service.CatalogoPresentacionService;
import com.proyecto.servicios.service.CifradoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Historial de pagos contra H2: aislamiento por usuario, orden, filtros inclusivos, paginación y nombres del catálogo. */
@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:historial;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class HistorialPagoServiceIntegrationTest {

    @SpringBootConfiguration
    @EntityScan(basePackages = {"com.proyecto.servicios.entity.onboarding", "com.proyecto.servicios.entity.productos"})
    @EnableJpaRepositories(basePackages = {"com.proyecto.servicios.repositorys.onboarding", "com.proyecto.servicios.repositorys.productos"})
    static class Configuracion {
    }

    private static final String AUTH = "Bearer token";
    private static final Long USUARIO = 7L;

    @Autowired private TransaccionPagoRepository transaccionPagoRepository;
    @Autowired private ProductoPostgresRepository productoRepository;
    @Autowired private CuentaRepository cuentaRepository;
    @Autowired private ClienteRepository clienteRepository;

    private final SesionService sesionService = mock(SesionService.class);
    private final CifradoService cifradoService = mock(CifradoService.class);
    private HistorialPagoService service;
    private CuentaEntity cuenta;

    @BeforeEach
    void setUp() {
        ClienteEntity cliente = clienteRepository.save(ClienteEntity.builder()
                .nombre("Ana").apellidoPaterno("Lopez").apellidoMaterno("Diaz").fechaNacimiento(LocalDate.of(1990, 1, 1))
                .curp("LODA900101MGTPZN01").rfc("LODA900101AB1").sexo("MUJER").nacionalidad("MEXICANA")
                .estadoCivil("SOLTERO").correo("ana@mail.com").telefonoMovil("4181234567").ocupacion("Ing")
                .empresa("UTNG").ingresoMensual(new BigDecimal("10000.00")).build());
        cuenta = cuentaRepository.save(CuentaEntity.builder().numeroCuenta("695429041188")
                .saldo(new BigDecimal("1000.00")).cliente(cliente).build());
        service = new HistorialPagoService(sesionService, transaccionPagoRepository, productoRepository,
                new PagoServicioMapper(cifradoService, new CatalogoPresentacionService("http://img/", new CatalogoProperties())));
        when(sesionService.autenticar(AUTH)).thenReturn(new SesionUsuario(USUARIO, "cliente1", SesionUsuario.ROL_CLIENTE, 10L));
    }

    @AfterEach
    void limpiar() {
        transaccionPagoRepository.deleteAll();
        productoRepository.deleteAll();
        cuentaRepository.deleteAll();
        clienteRepository.deleteAll();
    }

    private TransaccionPagoEntity guardar(Long usuario, EstatusTransaccionPago estatus, LocalDateTime fecha) {
        return transaccionPagoRepository.save(TransaccionPagoEntity.builder().usuarioId(usuario).cuenta(cuenta)
                .idServicio(1).idProducto(2).monto(new BigDecimal("100.00")).telefono("4181235678")
                .upc(UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase())
                .idempotencyKey(UUID.randomUUID().toString()).hashSolicitud("h".repeat(64))
                .estatus(estatus).fechaTransaccion(fecha).nombreServicio("Telcel").nombreProducto("Recarga").build());
    }

    private HistorialPagosResponse listar(FiltrosHistorialDto filtros, Integer pagina, Integer tamanio) {
        return service.listar(AUTH, HistorialPagosRequest.builder().filtros(filtros).pagina(pagina).tamanio(tamanio).build());
    }

    private static LocalDateTime dia(int d, int h, int m) {
        return LocalDateTime.of(2026, 11, d, h, m);
    }

    @Test
    void soloVeLasOperacionesDelUsuarioAutenticado() {
        guardar(USUARIO, EstatusTransaccionPago.CONFIRMADA, dia(10, 9, 0));
        guardar(99L, EstatusTransaccionPago.CONFIRMADA, dia(11, 9, 0));
        guardar(99L, EstatusTransaccionPago.RECHAZADA, dia(12, 9, 0));

        HistorialPagosResponse r = listar(null, null, null);

        assertEquals(1, r.getTotal());
        assertEquals(1, r.getElementos().size());
        assertFalse(r.isHayMas());
    }

    @Test
    void ordenDescendentePorFechaDeTransaccion() {
        TransaccionPagoEntity antigua = guardar(USUARIO, EstatusTransaccionPago.CONFIRMADA, dia(1, 8, 0));
        TransaccionPagoEntity reciente = guardar(USUARIO, EstatusTransaccionPago.CONFIRMADA, dia(13, 19, 39));
        TransaccionPagoEntity media = guardar(USUARIO, EstatusTransaccionPago.CONFIRMADA, dia(7, 12, 0));

        List<PagoServicioResponse> e = listar(null, null, null).getElementos();

        assertEquals(List.of(reciente.getUpc(), media.getUpc(), antigua.getUpc()),
                e.stream().map(PagoServicioResponse::getUpc).toList());
    }

    @Test
    void filtraPorEstatus() {
        guardar(USUARIO, EstatusTransaccionPago.CONFIRMADA, dia(1, 8, 0));
        guardar(USUARIO, EstatusTransaccionPago.RECHAZADA, dia(2, 8, 0));
        guardar(USUARIO, EstatusTransaccionPago.INDETERMINADA, dia(3, 8, 0));
        guardar(USUARIO, EstatusTransaccionPago.PENDIENTE, dia(4, 8, 0));

        for (EstatusTransaccionPago estatus : EstatusTransaccionPago.values()) {
            HistorialPagosResponse r = listar(FiltrosHistorialDto.builder().estatus(estatus).build(), null, null);
            assertEquals(1, r.getTotal(), estatus.name());
            assertEquals(estatus, r.getElementos().get(0).getEstatus());
        }
    }

    @Test
    void rangoDeFechasEsInclusivoConElDiaFinalCompleto() {
        guardar(USUARIO, EstatusTransaccionPago.CONFIRMADA, dia(9, 23, 59));   // fuera (día anterior)
        TransaccionPagoEntity inicio = guardar(USUARIO, EstatusTransaccionPago.CONFIRMADA, dia(10, 0, 0));
        TransaccionPagoEntity fin = guardar(USUARIO, EstatusTransaccionPago.CONFIRMADA, dia(12, 23, 59));
        guardar(USUARIO, EstatusTransaccionPago.CONFIRMADA, dia(13, 0, 0));    // fuera (día siguiente)

        HistorialPagosResponse r = listar(FiltrosHistorialDto.builder()
                .fechaDesde(LocalDate.of(2026, 11, 10)).fechaHasta(LocalDate.of(2026, 11, 12)).build(), null, null);

        assertEquals(List.of(fin.getUpc(), inicio.getUpc()), r.getElementos().stream().map(PagoServicioResponse::getUpc).toList());
        assertEquals(2, r.getTotal());
    }

    @Test
    void soloFechaDesdeYSoloFechaHasta() {
        guardar(USUARIO, EstatusTransaccionPago.CONFIRMADA, dia(5, 10, 0));
        guardar(USUARIO, EstatusTransaccionPago.CONFIRMADA, dia(15, 10, 0));

        assertEquals(1, listar(FiltrosHistorialDto.builder().fechaDesde(LocalDate.of(2026, 11, 10)).build(), null, null).getTotal());
        assertEquals(1, listar(FiltrosHistorialDto.builder().fechaHasta(LocalDate.of(2026, 11, 5)).build(), null, null).getTotal());
    }

    @Test
    void paginacionConTotalYHayMas() {
        for (int d = 1; d <= 5; d++) {
            guardar(USUARIO, EstatusTransaccionPago.CONFIRMADA, dia(d, 10, 0));
        }

        HistorialPagosResponse p0 = listar(null, 0, 2);
        HistorialPagosResponse p1 = listar(null, 1, 2);
        HistorialPagosResponse p2 = listar(null, 2, 2);
        HistorialPagosResponse p9 = listar(null, 9, 2);

        assertEquals(5, p0.getTotal());
        assertEquals(2, p0.getElementos().size());
        assertTrue(p0.isHayMas());
        assertEquals(0, p0.getPagina());
        assertEquals(2, p0.getTamanio());
        assertTrue(p1.isHayMas());
        assertEquals(1, p2.getElementos().size());
        assertFalse(p2.isHayMas());
        assertTrue(p9.getElementos().isEmpty());
        assertFalse(p9.isHayMas());
        assertEquals(20, listar(null, null, null).getTamanio());
    }

    @Test
    void laListaNuncaLlevaPinNiSaldoYEnmascaraLosDatos() {
        TransaccionPagoEntity tx = guardar(USUARIO, EstatusTransaccionPago.CONFIRMADA, dia(10, 9, 0));
        tx.setPinServicio("cifrado");
        tx.setFolio("FOL-1");
        tx.setPayloadSendtx("{secreto}");
        transaccionPagoRepository.save(tx);

        PagoServicioResponse e = listar(null, null, null).getElementos().get(0);

        assertNull(e.getPinServicio());
        assertNull(e.getSaldoRestante());
        assertEquals("********1188", e.getNumCuenta());
        assertEquals("******5678", e.getTelefonoEnmascarado());
        assertEquals("FOL-1", e.getFolio());
        assertEquals(1, e.getIdServicio());
        assertEquals(2, e.getIdProducto());
        assertEquals("http://img/servicios/1.png", e.getLogoUrl());
        org.mockito.Mockito.verifyNoInteractions(cifradoService);
    }

    @Test
    void filasAntiguasSinNombreTomanElDelCatalogo() {
        TransaccionPagoEntity antigua = guardar(USUARIO, EstatusTransaccionPago.CONFIRMADA, dia(10, 9, 0));
        antigua.setNombreServicio(null);
        antigua.setNombreProducto(null);
        transaccionPagoRepository.save(antigua);
        guardarProducto(1, 2, "Telcel", "Recarga $100");
        guardarProducto(1, 3, "Telcel", "Recarga $200");

        PagoServicioResponse e = listar(null, null, null).getElementos().get(0);

        assertEquals("Telcel", e.getServicio());
        assertEquals("Recarga $100", e.getProducto());
    }

    @Test
    void filasAntiguasSinCatalogoQuedanConNombresNulos() {
        TransaccionPagoEntity antigua = guardar(USUARIO, EstatusTransaccionPago.CONFIRMADA, dia(10, 9, 0));
        antigua.setNombreServicio(null);
        antigua.setNombreProducto(null);
        transaccionPagoRepository.save(antigua);

        PagoServicioResponse e = listar(null, null, null).getElementos().get(0);

        assertNull(e.getServicio());
        assertNull(e.getProducto());
    }

    @Test
    void ejecutivo_403NoTitular() {
        when(sesionService.autenticar(AUTH)).thenReturn(new SesionUsuario(1L, "ejec", SesionUsuario.ROL_EJECUTIVO, null));

        AccesoDenegadoException ex = assertThrows(AccesoDenegadoException.class, () -> listar(null, null, null));

        assertEquals(MotivoPago.NO_TITULAR, ((DetalleErrorPago) ex.getDetalle()).getMotivo());
    }

    private void guardarProducto(int idServicio, int idProducto, String servicio, String producto) {
        ProductoPostgresEntity p = new ProductoPostgresEntity();
        p.setTipoFront(1);
        p.setIdServicio(idServicio);
        p.setIdProducto(idProducto);
        p.setServicio(servicio);
        p.setProducto(producto);
        productoRepository.save(p);
    }
}
