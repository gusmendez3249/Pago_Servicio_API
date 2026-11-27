package com.proyecto.servicios.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cada socket creado solo puede negociar TLS 1.2 o 1.3. Se conecta a un ServerSocket local: crear el socket
 * abre la conexión TCP, pero el handshake TLS no ocurre hasta que se escribe, así que no se necesita red.
 */
class TlsEstrictoSocketFactoryTest {

    private static final Set<String> PERMITIDOS = Set.of("TLSv1.2", "TLSv1.3");

    private final TlsEstrictoSocketFactory factory = new TlsEstrictoSocketFactory();
    private ServerSocket servidor;
    private InetAddress local;

    @BeforeEach
    void abrirServidorLocal() throws Exception {
        local = InetAddress.getLoopbackAddress();
        servidor = new ServerSocket(0, 50, local);
    }

    @AfterEach
    void cerrar() throws Exception {
        servidor.close();
    }

    private static void soloTlsModerno(Socket socket) throws Exception {
        try (socket) {
            SSLSocket ssl = assertInstanceOf(SSLSocket.class, socket);
            List<String> habilitados = List.of(ssl.getEnabledProtocols());
            assertFalse(habilitados.isEmpty());
            assertTrue(PERMITIDOS.containsAll(habilitados), "protocolos habilitados: " + habilitados);
        }
    }

    @Test
    void todasLasVariantesDeCreacionRestringenLosProtocolos() throws Exception {
        int puerto = servidor.getLocalPort();
        String host = local.getHostAddress();

        soloTlsModerno(factory.createSocket());
        soloTlsModerno(factory.createSocket(host, puerto));
        soloTlsModerno(factory.createSocket(host, puerto, local, 0));
        soloTlsModerno(factory.createSocket(local, puerto));
        soloTlsModerno(factory.createSocket(local, puerto, local, 0));
        soloTlsModerno(factory.createSocket(new Socket(local, puerto), host, puerto, true));
    }

    @Test
    void exponeLasSuitesDeCifradoDeLaJvm() {
        assertTrue(factory.getDefaultCipherSuites().length > 0);
        assertTrue(factory.getSupportedCipherSuites().length >= factory.getDefaultCipherSuites().length);
    }
}
