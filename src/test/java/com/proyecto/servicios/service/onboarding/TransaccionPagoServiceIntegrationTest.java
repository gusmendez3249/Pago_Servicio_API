package com.proyecto.servicios.service.onboarding;

import com.proyecto.servicios.entity.onboarding.ClienteEntity;
import com.proyecto.servicios.entity.onboarding.CuentaEntity;
import com.proyecto.servicios.entity.onboarding.TransaccionPagoEntity;
import com.proyecto.servicios.enums.EstatusTransaccionPago;
import com.proyecto.servicios.exception.ReglaNegocioException;
import com.proyecto.servicios.repositorys.onboarding.ClienteRepository;
import com.proyecto.servicios.repositorys.onboarding.CuentaRepository;
import com.proyecto.servicios.repositorys.onboarding.TransaccionPagoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integración con base de datos (H2): reserva atómica del saldo, índice único de idempotencia, reintegro único
 * y concurrencia real entre hilos. Cada método del servicio corre en su propia transacción, como en producción.
 */
@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:pagos;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class TransaccionPagoServiceIntegrationTest {

    @SpringBootConfiguration
    @EntityScan(basePackages = "com.proyecto.servicios.entity.onboarding")
    @EnableJpaRepositories(basePackages = "com.proyecto.servicios.repositorys.onboarding")
    @Import(TransaccionPagoService.class)
    static class Configuracion {

        /** Sin llave real: las pruebas de saldo y estatus no manejan PIN de tarjeta de regalo. */
        @org.springframework.context.annotation.Bean
        com.proyecto.servicios.service.CifradoService cifradoService() {
            return org.mockito.Mockito.mock(com.proyecto.servicios.service.CifradoService.class);
        }
    }

    @Autowired private TransaccionPagoService service;
    @Autowired private CuentaRepository cuentaRepository;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private TransaccionPagoRepository transaccionPagoRepository;
    @Autowired private com.proyecto.servicios.service.CifradoService cifradoService;

    private Long cuentaId;

    @BeforeEach
    void setUp() {
        ClienteEntity cliente = clienteRepository.save(ClienteEntity.builder()
                .nombre("Ana").apellidoPaterno("Lopez").apellidoMaterno("Diaz").fechaNacimiento(LocalDate.of(1990, 1, 1))
                .curp("LODA900101MGTPZN01").rfc("LODA900101AB1").sexo("MUJER").nacionalidad("MEXICANA")
                .estadoCivil("SOLTERO").correo("ana@mail.com").telefonoMovil("4181234567").ocupacion("Ing")
                .empresa("UTNG").ingresoMensual(new BigDecimal("10000.00")).build());
        cuentaId = cuentaRepository.save(CuentaEntity.builder().numeroCuenta("695429041188")
                .saldo(new BigDecimal("1000.00")).cliente(cliente).build()).getId();
    }

    @AfterEach
    void limpiar() {
        transaccionPagoRepository.deleteAll();
        cuentaRepository.deleteAll();
        clienteRepository.deleteAll();
    }

    private static TransaccionPagoEntity nueva(String monto, String upc) {
        return TransaccionPagoEntity.builder().usuarioId(1L).idServicio(1).idProducto(2)
                .monto(new BigDecimal(monto)).telefono("4181234567").upc(upc)
                .idempotencyKey(UUID.randomUUID().toString()).hashSolicitud("h".repeat(64))
                .fechaTransaccion(LocalDateTime.now()).build();
    }

    private BigDecimal saldo() {
        return service.saldo(cuentaId);
    }

    @Test
    void reservar_DescuentaYRegistraPENDIENTE() {
        TransaccionPagoEntity tx = service.reservar(cuentaId, nueva("100.50", "ABCDE12345"));

        assertEquals(new BigDecimal("899.50"), saldo());
        assertEquals(EstatusTransaccionPago.PENDIENTE,
                transaccionPagoRepository.findById(tx.getId()).orElseThrow().getEstatus());
    }

    @Test
    void saldoInsuficiente_NoDescuentaNiRegistra() {
        assertThrows(ReglaNegocioException.class, () -> service.reservar(cuentaId, nueva("1000.01", "ABCDE12345")));

        assertEquals(new BigDecimal("1000.00"), saldo());
        assertEquals(0, transaccionPagoRepository.count());
    }

    @Test
    void llaveDuplicada_SeRechazaYElSaldoNoSeDescuentaDosVeces() {
        TransaccionPagoEntity primera = nueva("100.00", "AAAAA11111");
        service.reservar(cuentaId, primera);
        TransaccionPagoEntity repetida = nueva("100.00", "BBBBB22222");
        repetida.setIdempotencyKey(primera.getIdempotencyKey());

        assertThrows(DataIntegrityViolationException.class, () -> service.reservar(cuentaId, repetida));
        assertEquals(new BigDecimal("900.00"), saldo(), "la transacción que falló revierte también su descuento");
        assertEquals(1, transaccionPagoRepository.count());
    }

    @Test
    void reintegro_SoloOcurreUnaVez() {
        TransaccionPagoEntity tx = service.reservar(cuentaId, nueva("300.00", "ABCDE12345"));

        assertTrue(service.rechazarYReintegrar(tx.getId(), cuentaId, tx.getMonto(), "05", "rechazo"));
        assertFalse(service.rechazarYReintegrar(tx.getId(), cuentaId, tx.getMonto(), "05", "rechazo"));
        assertFalse(service.confirmar(tx.getId(), "01", "tarde"), "una operación cerrada no se reabre");

        assertEquals(new BigDecimal("1000.00"), saldo());
    }

    @Test
    void indeterminada_RetieneElMontoHastaConciliar() {
        TransaccionPagoEntity tx = service.reservar(cuentaId, nueva("250.00", "ABCDE12345"));

        assertTrue(service.marcarIndeterminada(tx.getId(), null, "timeout"));
        assertEquals(new BigDecimal("750.00"), saldo());

        assertTrue(service.confirmar(tx.getId(), null, "conciliada"));
        assertEquals(EstatusTransaccionPago.CONFIRMADA,
                transaccionPagoRepository.findById(tx.getId()).orElseThrow().getEstatus());
        assertEquals(new BigDecimal("750.00"), saldo());
    }

    @Test
    void reservasSimultaneas_NuncaDejanSaldoNegativo() throws Exception {
        int hilos = 10;
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Boolean>> resultados = new ArrayList<>();
        for (int i = 0; i < hilos; i++) {
            String upc = String.format("CONC%06d", i);
            Callable<Boolean> tarea = () -> {
                salida.await();
                try {
                    service.reservar(cuentaId, nueva("200.00", upc));
                    return true;
                } catch (ReglaNegocioException saldoInsuficiente) {
                    return false;
                }
            };
            resultados.add(pool.submit(tarea));
        }
        salida.countDown();

        int exitosas = 0;
        for (Future<Boolean> r : resultados) {
            if (r.get()) {
                exitosas++;
            }
        }
        pool.shutdown();

        assertEquals(5, exitosas, "1000.00 alcanza exactamente para 5 pagos de 200.00");
        assertEquals(0, saldo().compareTo(BigDecimal.ZERO));
        assertEquals(5, transaccionPagoRepository.count());
    }

    // ---------------------------------------------------------------- Sprint 3: folio, motivo, confirmTx y cuentas

    @Test
    void confirmarConDatos_GuardaFolioIdTxComisionYElPinCifrado() {
        org.mockito.Mockito.when(cifradoService.cifrar("GIFT-1234")).thenReturn("PIN-CIFRADO");
        TransaccionPagoEntity tx = service.reservar(cuentaId, nueva("100.00", "ABCDE12345"));
        com.proyecto.servicios.model.gestopago.DatosProveedor datos = new com.proyecto.servicios.model.gestopago.DatosProveedor(
                "TX-1", "FOL-1", new BigDecimal("2.50"), new BigDecimal("100.00"), "20260101120500", "REF", "GIFT-1234", "Gracias");

        assertTrue(service.confirmar(tx.getId(), "01", "OK", datos));

        TransaccionPagoEntity guardada = transaccionPagoRepository.findById(tx.getId()).orElseThrow();
        assertEquals(EstatusTransaccionPago.CONFIRMADA, guardada.getEstatus());
        assertEquals("FOL-1", guardada.getFolio());
        assertEquals("TX-1", guardada.getIdTx());
        assertEquals(0, new BigDecimal("2.50").compareTo(guardada.getComision()));
        assertEquals("Gracias", guardada.getLeyenda());
        assertEquals("PIN-CIFRADO", guardada.getPinServicio(), "el PIN solo se guarda cifrado");
        assertEquals(new BigDecimal("900.00"), saldo());
        assertFalse(service.confirmar(tx.getId(), "01", "otra vez", datos), "no se confirma dos veces");
    }

    @Test
    void confirmacionesReales_QuedanConOrigenGESTOPAGO() {
        TransaccionPagoEntity conDatos = service.reservar(cuentaId, nueva("10.00", "AAAAA11111"));
        TransaccionPagoEntity manual = service.reservar(cuentaId, nueva("10.00", "BBBBB22222"));

        assertTrue(service.confirmar(conDatos.getId(), "01", "OK", new com.proyecto.servicios.model.gestopago.DatosProveedor(
                "TX-1", "FOL-1", null, null, null, null, null, null)));
        assertTrue(service.confirmar(manual.getId(), null, "conciliada"));

        assertEquals(com.proyecto.servicios.enums.OrigenConfirmacion.GESTOPAGO,
                transaccionPagoRepository.findById(conDatos.getId()).orElseThrow().getOrigenConfirmacion());
        assertEquals(com.proyecto.servicios.enums.OrigenConfirmacion.GESTOPAGO,
                transaccionPagoRepository.findById(manual.getId()).orElseThrow().getOrigenConfirmacion());
    }

    @Test
    void rechazoConMotivo_ReintegraUnaSolaVezYGuardaElMotivo() {
        TransaccionPagoEntity tx = service.reservar(cuentaId, nueva("300.00", "ABCDE12345"));

        assertTrue(service.rechazarYReintegrar(tx.getId(), cuentaId, tx.getMonto(), "70", "no aplicada",
                com.proyecto.servicios.enums.MotivoPago.RECHAZADO_PROVEEDOR));
        assertFalse(service.rechazarYReintegrar(tx.getId(), cuentaId, tx.getMonto(), "70", "no aplicada",
                com.proyecto.servicios.enums.MotivoPago.RECHAZADO_PROVEEDOR));

        assertEquals(new BigDecimal("1000.00"), saldo());
        assertEquals(com.proyecto.servicios.enums.MotivoPago.RECHAZADO_PROVEEDOR,
                transaccionPagoRepository.findById(tx.getId()).orElseThrow().getMotivo());
    }

    @Test
    void reclamoDeConsulta_SoloUnaIndeterminadaYConSeparacionMinima() {
        TransaccionPagoEntity tx = service.reservar(cuentaId, nueva("100.00", "ABCDE12345"));
        LocalDateTime ahora = LocalDateTime.now();

        assertFalse(service.reclamarConsultaConfirmacion(tx.getId(), ahora, ahora.minusSeconds(30)), "una PENDIENTE no se consulta");

        service.marcarIndeterminada(tx.getId(), null, "timeout");
        assertTrue(service.reclamarConsultaConfirmacion(tx.getId(), ahora, ahora.minusSeconds(30)));
        assertFalse(service.reclamarConsultaConfirmacion(tx.getId(), ahora.plusSeconds(10), ahora.minusSeconds(20)),
                "10 s despues de la ultima consulta no se vuelve a reclamar");
        assertTrue(service.reclamarConsultaConfirmacion(tx.getId(), ahora.plusSeconds(31), ahora.plusSeconds(1)));

        TransaccionPagoEntity guardada = transaccionPagoRepository.findById(tx.getId()).orElseThrow();
        assertEquals(2, guardada.getIntentosConfirmacion());
        assertNotNull(guardada.getFechaUltimaConfirmacion());

        service.confirmar(tx.getId(), "06", "ok");
        assertFalse(service.reclamarConsultaConfirmacion(tx.getId(), ahora.plusSeconds(120), ahora.plusSeconds(90)),
                "una operacion resuelta no se consulta");
    }

    @Test
    void cuentasDelCliente_SoloLasActivas() {
        ClienteEntity cliente = clienteRepository.findAll().get(0);
        cuentaRepository.save(CuentaEntity.builder().numeroCuenta("111111111111").estatus("BLOQUEADA").cliente(cliente).build());

        java.util.List<CuentaEntity> activas = cuentaRepository.findByClienteIdAndEstatusOrderByIdAsc(cliente.getId(), "ACTIVA");

        assertEquals(1, activas.size());
        assertEquals("695429041188", activas.get(0).getNumeroCuenta());
    }
}
