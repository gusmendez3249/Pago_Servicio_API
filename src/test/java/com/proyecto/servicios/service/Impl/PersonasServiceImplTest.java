package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.sf.Personas;
import com.proyecto.servicios.exception.RecursoNoEncontradoException;
import com.proyecto.servicios.model.EliminaPersonaRequest;
import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.PersonaResponse;
import com.proyecto.servicios.model.PersonasRequest;
import com.proyecto.servicios.repositorys.sf.PersonasRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Alta, baja y actualización de personas. */
@ExtendWith(MockitoExtension.class)
class PersonasServiceImplTest {

    @Mock private PersonasRepository repository;
    @InjectMocks private PersonasServiceImpl service;

    private static PersonasRequest request() {
        PersonasRequest request = new PersonasRequest();
        request.setNombre("Juan");
        request.setApellidoP("Perez");
        request.setApellidoMaterno("Lopez");
        return request;
    }

    private static Personas persona() {
        Personas persona = new Personas();
        persona.setNombre("Juan");
        persona.setApellidoP("Viejo");
        persona.setApellidoMaterno("Viejo");
        return persona;
    }

    @Test
    void creaPersonaGuardaYDevuelveSusDatos() {
        PersonaResponse response = service.creaPersona(request());

        ArgumentCaptor<Personas> guardada = ArgumentCaptor.forClass(Personas.class);
        verify(repository).save(guardada.capture());
        assertEquals("Juan", guardada.getValue().getNombre());
        assertEquals(1, response.getCodigo());
        assertEquals("Exito", response.getMensaje());
        assertEquals("Perez", response.getApellidoP());
        assertEquals("Lopez", response.getApellidoMaterno());
    }

    @Test
    void eliminaPersonaExistente() {
        Personas persona = persona();
        when(repository.findFirstByNombreOrderByIdAsc("Juan")).thenReturn(Optional.of(persona));
        EliminaPersonaRequest request = new EliminaPersonaRequest();
        request.setNombre("Juan");

        GenericResponse response = service.eliminaPersona(request);

        verify(repository).delete(persona);
        assertEquals(0, response.getCodigo());
    }

    @Test
    void eliminarPersonaInexistenteLanzaNoEncontrado() {
        when(repository.findFirstByNombreOrderByIdAsc("Nadie")).thenReturn(Optional.empty());
        EliminaPersonaRequest request = new EliminaPersonaRequest();
        request.setNombre("Nadie");

        assertThrows(RecursoNoEncontradoException.class, () -> service.eliminaPersona(request));
        verify(repository, never()).delete(any());
    }

    @Test
    void actualizaApellidosDePersonaExistente() {
        Personas persona = persona();
        when(repository.findFirstByNombreOrderByIdAsc("Juan")).thenReturn(Optional.of(persona));

        GenericResponse response = service.actualizaPersona(request());

        verify(repository).save(persona);
        assertEquals("Perez", persona.getApellidoP());
        assertEquals("Lopez", persona.getApellidoMaterno());
        assertEquals(0, response.getCodigo());
    }

    @Test
    void actualizarPersonaInexistenteLanzaNoEncontrado() {
        when(repository.findFirstByNombreOrderByIdAsc("Juan")).thenReturn(Optional.empty());
        PersonasRequest request = request();

        assertThrows(RecursoNoEncontradoException.class, () -> service.actualizaPersona(request));
        verify(repository, never()).save(any());
    }
}
