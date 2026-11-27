package com.proyecto.servicios.service.onboarding;

import com.proyecto.servicios.dto.onboarding.ConsultaCuentaAdminRequest;
import com.proyecto.servicios.dto.onboarding.HistorialRecargasRequest;
import com.proyecto.servicios.dto.onboarding.RecargaSaldoRequest;
import com.proyecto.servicios.dto.onboarding.RecargaSaldoResponse;
import com.proyecto.servicios.entity.onboarding.ClienteEntity;
import com.proyecto.servicios.entity.onboarding.CuentaEntity;
import com.proyecto.servicios.exception.AccesoDenegadoException;
import com.proyecto.servicios.exception.ClienteInactivoException;
import com.proyecto.servicios.exception.OperacionDuplicadaException;
import com.proyecto.servicios.exception.RecursoNoEncontradoException;
import com.proyecto.servicios.exception.ReglaNegocioException;
import com.proyecto.servicios.repositorys.onboarding.ClienteRepository;
import com.proyecto.servicios.repositorys.onboarding.CuentaRepository;
import com.proyecto.servicios.repositorys.onboarding.RecargaSaldoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.springframework.test.util.ReflectionTestUtils;
import com.proyecto.servicios.enums.MotivoPago;
import com.proyecto.servicios.exception.TopeRecargaException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Recargas de saldo del panel de administrador contra base de datos (H2), incluida la concurrencia. */
@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:recargas;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RecargaSaldoServiceIntegrationTest {

    @SpringBootConfiguration
    @EntityScan(basePackages = "com.proyecto.servicios.entity.onboarding")
    @EnableJpaRepositories(basePackages = "com.proyecto.servicios.repositorys.onboarding")
    @Import(RecargaSaldoService.class)
    static class Configuracion {
        @Bean
        SesionService sesionService() {
            return mock(SesionService.class);
        }

        @Bean
        TransactionTemplate transactionTemplate(PlatformTransactionManager tm) {
            return new TransactionTemplate(tm);
        }
    }

    private static final String ADMIN = "Bearer admin";
    private static final String CLIENTE = "Bearer cliente";

    @Autowired private RecargaSaldoService service;
    @Autowired private SesionService sesionService;
    @Autowired private CuentaRepository cuentaRepository;
    @Autowired private ClienteRepository clienteRepository;
    @Autowired private RecargaSaldoRepository recargaRepository;

    private CuentaEntity cuenta;

    @BeforeEach
    void setUp() {
        reset(sesionService);
        when(sesionService.autenticar(ADMIN)).thenReturn(new SesionUsuario(1L, "admin", SesionUsuario.ROL_EJECUTIVO, null));
        when(sesionService.autenticar(CLIENTE)).thenReturn(new SesionUsuario(2L, "ana", SesionUsuario.ROL_CLIENTE, 9L));
        ClienteEntity cliente = clienteRepository.save(ClienteEntity.builder()
                .nombre("Ana").apellidoPaterno("Lopez").apellidoMaterno("Diaz").fechaNacimiento(LocalDate.of(1990, 1, 1))
                .curp("LODA900101MGTPZN01").rfc("LODA900101AB1").sexo("FEMENINO").nacionalidad("MEXICANA")
                .estadoCivil("SOLTERO").correo("ana@mail.com").telefonoMovil("4181234567").ocupacion("Ing")
                .empresa("UTNG").ingresoMensual(new BigDecimal("10000.00")).activo(true).build());
        cuenta = cuentaRepository.save(CuentaEntity.builder().numeroCuenta("695429041188").cliente(cliente).build());
    }

    @AfterEach
    void limpiar() {
        ReflectionTestUtils.setField(service, "topeDiarioCuenta", new BigDecimal("100000.00"));
        ReflectionTestUtils.setField(service, "topeDiarioEjecutivo", new BigDecimal("200000.00"));
        recargaRepository.deleteAll();
        cuentaRepository.deleteAll();
        clienteRepository.deleteAll();
    }

    private static RecargaSaldoRequest recarga(String monto) {
        return new RecargaSaldoRequest("695429041188", new BigDecimal(monto));
    }

    private BigDecimal saldo() {
        return cuentaRepository.findSaldoById(cuenta.getId()).orElseThrow();
    }

    @Test
    void cuentaNuevaNaceEnCeroYLaRecargaAbonaYQuedaAuditada() {
        assertEquals(new BigDecimal("0.00"), saldo());

        RecargaSaldoResponse r = service.recargar(ADMIN, UUID.randomUUID().toString(), recarga("500.00"));

        assertEquals(new BigDecimal("500.00"), saldo());
        assertEquals(new BigDecimal("500.00"), r.getSaldoResultante());
        assertEquals("********1188", r.getNumCuentaEnmascarada());
        assertEquals("Ana Lopez Diaz", r.getTitular());
        assertTrue(r.getFolio().matches("^R-\\d{6}$"));
        assertEquals(1, recargaRepository.count());
        assertEquals(1L, recargaRepository.findAll().get(0).getEjecutivoId());
    }

    @Test
    void mismaLlaveYMismosDatos_DevuelveLaRecargaOriginalSinAbonarDosVeces() {
        String llave = UUID.randomUUID().toString();
        RecargaSaldoResponse primera = service.recargar(ADMIN, llave, recarga("100.00"));
        RecargaSaldoResponse repetida = service.recargar(ADMIN, llave.toUpperCase(), recarga("100.00"));

        assertEquals(primera.getFolio(), repetida.getFolio());
        assertEquals(new BigDecimal("100.00"), saldo());
        assertThrows(OperacionDuplicadaException.class, () -> service.recargar(ADMIN, llave, recarga("999.00")));
    }

    @Test
    void diezRecargasSimultaneasConLaMismaLlave_AbonanUnaSolaVez() throws Exception {
        String llave = UUID.randomUUID().toString();
        ExecutorService hilos = Executors.newFixedThreadPool(10);
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<Boolean>> res = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            res.add(hilos.submit(() -> {
                salida.await();
                try {
                    service.recargar(ADMIN, llave, recarga("50.00"));
                    return true;
                } catch (OperacionDuplicadaException e) {
                    return false;
                }
            }));
        }
        salida.countDown();
        for (Future<Boolean> f : res) {
            f.get();
        }
        hilos.shutdown();
        assertEquals(new BigDecimal("50.00"), saldo(), "un solo abono");
        assertEquals(1, recargaRepository.count());
    }

    @Test
    void unClienteNoPuedeConsultarNiRecargar() {
        assertThrows(AccesoDenegadoException.class, () -> service.recargar(CLIENTE, UUID.randomUUID().toString(), recarga("10.00")));
        assertThrows(AccesoDenegadoException.class,
                () -> service.consultarCuenta(CLIENTE, new ConsultaCuentaAdminRequest("695429041188")));
        assertEquals(new BigDecimal("0.00"), saldo());
    }

    @Test
    void cuentaInexistenteInactivaOSinLlave() {
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.recargar(ADMIN, UUID.randomUUID().toString(), new RecargaSaldoRequest("000000000000", BigDecimal.TEN)));
        assertThrows(ReglaNegocioException.class, () -> service.recargar(ADMIN, "no-uuid", recarga("10.00")));

        cuenta.setEstatus("INACTIVA");
        cuentaRepository.save(cuenta);
        assertFalse(service.consultarCuenta(ADMIN, new ConsultaCuentaAdminRequest("695429041188")).isRecargable());
        assertThrows(ClienteInactivoException.class, () -> service.recargar(ADMIN, UUID.randomUUID().toString(), recarga("10.00")));
        assertEquals(new BigDecimal("0.00"), saldo());
    }

    @Test
    void historialDeLaMasRecienteALaMasAntigua() {
        service.recargar(ADMIN, UUID.randomUUID().toString(), recarga("10.00"));
        service.recargar(ADMIN, UUID.randomUUID().toString(), recarga("20.00"));

        var h = service.historial(ADMIN, new HistorialRecargasRequest(null));
        assertEquals(2, h.getTotalRecargas());
        assertEquals(new BigDecimal("30.00"), h.getRecargas().get(0).getSaldoResultante());
    }

    @Test
    void recargaQueRebasaElTopeDiarioDeLaCuentaSeRechazaSinAbonar() {
        ReflectionTestUtils.setField(service, "topeDiarioCuenta", new BigDecimal("100.00"));
        service.recargar(ADMIN, UUID.randomUUID().toString(), recarga("80.00"));

        TopeRecargaException e = assertThrows(TopeRecargaException.class,
                () -> service.recargar(ADMIN, UUID.randomUUID().toString(), recarga("20.01")));
        assertEquals(MotivoPago.TOPE_DIARIO_CUENTA, e.getMotivo());
        assertEquals(new BigDecimal("80.00"), saldo());
        assertEquals(1, recargaRepository.count());
        service.recargar(ADMIN, UUID.randomUUID().toString(), recarga("20.00"));
        assertEquals(new BigDecimal("100.00"), saldo());
    }

    @Test
    void recargaQueRebasaElTopeDiarioDelEjecutivoSeRechaza() {
        ReflectionTestUtils.setField(service, "topeDiarioEjecutivo", new BigDecimal("50.00"));

        TopeRecargaException e = assertThrows(TopeRecargaException.class,
                () -> service.recargar(ADMIN, UUID.randomUUID().toString(), recarga("50.01")));
        assertEquals(MotivoPago.TOPE_DIARIO_EJECUTIVO, e.getMotivo());
        assertEquals(0, saldo().compareTo(BigDecimal.ZERO));
    }

    @Test
    void recargasSimultaneasNoRebasanElTopeDeLaCuenta() throws Exception {
        ReflectionTestUtils.setField(service, "topeDiarioCuenta", new BigDecimal("100.00"));
        ExecutorService pool = Executors.newFixedThreadPool(10);
        List<Future<Boolean>> resultados = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            resultados.add(pool.submit(() -> {
                try {
                    service.recargar(ADMIN, UUID.randomUUID().toString(), recarga("30.00"));
                    return true;
                } catch (TopeRecargaException e) {
                    return false;
                }
            }));
        }
        int aplicadas = 0;
        for (Future<Boolean> r : resultados) {
            if (r.get()) aplicadas++;
        }
        pool.shutdown();
        assertEquals(3, aplicadas);
        assertEquals(new BigDecimal("90.00"), saldo());
    }
}
