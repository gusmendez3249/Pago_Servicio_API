package com.proyecto.servicios.service.onboarding;

import com.proyecto.servicios.dto.onboarding.FiltrosClienteDto;
import com.proyecto.servicios.entity.onboarding.ClienteEntity;
import com.proyecto.servicios.entity.onboarding.CuentaEntity;
import com.proyecto.servicios.repositorys.onboarding.ClienteRepository;
import com.proyecto.servicios.repositorys.onboarding.CuentaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Búsqueda de clientes (banderas 2, 3 y 4) contra una base real H2: coincidencia exacta para identificar a un
 * cliente, "contiene" para consultar, y restricción al propio registro cuando quien consulta es un cliente.
 */
@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:busqueda;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
})
class ClienteBusquedaIntegrationTest {

    @SpringBootConfiguration
    @EntityScan(basePackages = "com.proyecto.servicios.entity.onboarding")
    @EnableJpaRepositories(basePackages = "com.proyecto.servicios.repositorys.onboarding")
    static class Configuracion {
    }

    @Autowired private ClienteRepository clienteRepository;
    @Autowired private CuentaRepository cuentaRepository;

    private ClienteEntity ana;
    private ClienteEntity beto;

    private ClienteEntity cliente(String nombre, String curp, String rfc, String correo, String cuenta) {
        ClienteEntity c = clienteRepository.save(ClienteEntity.builder()
                .nombre(nombre).apellidoPaterno("Lopez").apellidoMaterno("Diaz").fechaNacimiento(LocalDate.of(1990, 1, 1))
                .curp(curp).rfc(rfc).sexo("MUJER").nacionalidad("MEXICANA").estadoCivil("SOLTERO")
                .correo(correo).telefonoMovil("4181234567").ocupacion("Ing").empresa("UTNG")
                .ingresoMensual(new BigDecimal("10000.00")).activo(true).build());
        cuentaRepository.save(CuentaEntity.builder().numeroCuenta(cuenta).cliente(c).build());
        return c;
    }

    @BeforeEach
    void setUp() {
        ana = cliente("Ana", "LODA900101MGTPZN01", "LODA900101AB1", "ana@mail.com", "695429041188");
        beto = cliente("Beto", "LOBB900101HGTPZN02", "LOBB900101AB2", "beto@mail.com", "695429049999");
    }

    private List<ClienteEntity> buscar(FiltrosClienteDto filtros, boolean exacta, Long soloClienteId) {
        return clienteRepository.findAll(ClienteLayawayService.especificacionConsulta(filtros, exacta, soloClienteId));
    }

    @Test
    void curpExactaIgnoraMayusculas() {
        List<ClienteEntity> encontrados = buscar(FiltrosClienteDto.builder().curp("loda900101mgtpzn01").build(), true, null);

        assertEquals(List.of(ana.getId()), encontrados.stream().map(ClienteEntity::getId).toList());
    }

    @Test
    void curpParcialSoloCoincideEnConsulta() {
        FiltrosClienteDto parcial = FiltrosClienteDto.builder().curp("900101").build();

        assertEquals(2, buscar(parcial, false, null).size());
        assertTrue(buscar(parcial, true, null).isEmpty(), "para modificar se exige la llave completa");
    }

    @Test
    void rfcExactoYParcial() {
        assertEquals(1, buscar(FiltrosClienteDto.builder().rfc("LOBB900101AB2").build(), true, null).size());
        assertEquals(2, buscar(FiltrosClienteDto.builder().rfc("lo").build(), false, null).size());
    }

    @Test
    void numeroDeCuentaExactoYParcial() {
        List<ClienteEntity> exacto = buscar(FiltrosClienteDto.builder().numeroCuenta("695429049999").build(), true, null);
        assertEquals(beto.getId(), exacto.get(0).getId());

        assertEquals(2, buscar(FiltrosClienteDto.builder().numeroCuenta("6954290").build(), false, null).size());
    }

    @Test
    void unClienteSoloEncuentraSuPropioRegistro() {
        FiltrosClienteDto todos = FiltrosClienteDto.builder().curp("LO").build();

        List<ClienteEntity> propios = buscar(todos, false, ana.getId());

        assertEquals(List.of(ana.getId()), propios.stream().map(ClienteEntity::getId).toList());
    }

    @Test
    void sinFiltrosDevuelveTodos() {
        assertEquals(2, buscar(new FiltrosClienteDto(), false, null).size());
    }
}
