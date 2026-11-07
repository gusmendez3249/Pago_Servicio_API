package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.sf.Personas;
import com.proyecto.servicios.exception.RecursoNoEncontradoException;
import com.proyecto.servicios.model.EliminaPersonaRequest;
import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.model.PersonaResponse;
import com.proyecto.servicios.model.PersonasRequest;
import com.proyecto.servicios.repositorys.sf.PersonasRepository;
import com.proyecto.servicios.service.PersonaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;


@Service
@Slf4j
@RequiredArgsConstructor
public class PersonasServiceImpl implements PersonaService {

    private static final int CODIGO_CREADA = 1;
    private static final int CODIGO_EXITO = 0;
    private static final String PERSONA_NO_EXISTE = "La persona no existe";

    private final PersonasRepository personasRepository;

    @Override
    public PersonaResponse creaPersona(PersonasRequest personasRequest) {
        Personas persona = new Personas();
        persona.setNombre(personasRequest.getNombre());
        persona.setApellidoP(personasRequest.getApellidoP());
        persona.setApellidoMaterno(personasRequest.getApellidoMaterno());
        personasRepository.save(persona);

        PersonaResponse respuesta = new PersonaResponse();
        BeanUtils.copyProperties(persona, respuesta);
        respuesta.setCodigo(CODIGO_CREADA);
        respuesta.setMensaje("Exito");
        return respuesta;
    }

    @Override
    public GenericResponse eliminaPersona(EliminaPersonaRequest eliminaPersonaRequest) {
        Personas persona = buscarPorNombre(eliminaPersonaRequest.getNombre());
        personasRepository.delete(persona);
        return exito("La persona ha sido eliminada correctamente");
    }

    @Override
    public GenericResponse actualizaPersona(PersonasRequest personasRequest) {
        Personas persona = buscarPorNombre(personasRequest.getNombre());
        persona.setApellidoP(personasRequest.getApellidoP());
        persona.setApellidoMaterno(personasRequest.getApellidoMaterno());
        personasRepository.save(persona);
        return exito("la persona ha sido actualizada correctamente");
    }

    private Personas buscarPorNombre(String nombre) {
        return personasRepository.findFirstByNombreOrderByIdAsc(nombre)
                .orElseThrow(() -> new RecursoNoEncontradoException(PERSONA_NO_EXISTE));
    }

    private static GenericResponse exito(String mensaje) {
        GenericResponse respuesta = new GenericResponse();
        respuesta.setCodigo(CODIGO_EXITO);
        respuesta.setMensaje(mensaje);
        return respuesta;
    }
}
