package com.proyecto.servicios.config;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * Fábrica de sockets que solo negocia TLS 1.3 o TLS 1.2. GestoPago rechaza TLS 1.0/1.1 (verificado contra
 * gestopago.portalventas.net); además se fija explícitamente para no depender de la configuración de la JVM.
 * Usa el almacén de confianza por defecto: el certificado del servidor y su nombre siempre se validan.
 */
public class TlsEstrictoSocketFactory extends SSLSocketFactory {

    private static final String[] PROTOCOLOS_PERMITIDOS = {"TLSv1.3", "TLSv1.2"};

    private final SSLSocketFactory delegado;
    private final String[] protocolos;

    /** Contexto TLS por defecto de la JVM (cacerts) y solo TLS 1.3 / 1.2. */
    public TlsEstrictoSocketFactory() {
        this(contextoJvm(), PROTOCOLOS_PERMITIDOS);
    }

    /**
     * Contexto TLS propio (p. ej. el SSL bundle con el truststore de GestoPago) y los protocolos configurados.
     * Solo se conservan los que estén permitidos (TLS 1.3 / 1.2) y la JVM soporte.
     */
    public TlsEstrictoSocketFactory(SSLContext contexto, String[] protocolosSolicitados) {
        this.delegado = contexto.getSocketFactory();
        java.util.List<String> soportados = Arrays.asList(contexto.getSupportedSSLParameters().getProtocols());
        java.util.List<String> permitidos = Arrays.asList(PROTOCOLOS_PERMITIDOS);
        String[] pedidos = protocolosSolicitados == null || protocolosSolicitados.length == 0
                ? PROTOCOLOS_PERMITIDOS : protocolosSolicitados;
        this.protocolos = Arrays.stream(pedidos)
                .filter(permitidos::contains)
                .filter(soportados::contains)
                .toArray(String[]::new);
        if (this.protocolos.length == 0) {
            throw new IllegalStateException("La JVM no soporta TLS 1.2 ni TLS 1.3");
        }
    }

    public static SSLContext contextoJvm() {
        try {
            return SSLContext.getDefault();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("No se pudo inicializar el contexto TLS", exception);
        }
    }

    public String[] getProtocolos() {
        return protocolos.clone();
    }

    private Socket restringir(Socket socket) {
        if (socket instanceof SSLSocket ssl) {
            ssl.setEnabledProtocols(protocolos);
        }
        return socket;
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return delegado.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return delegado.getSupportedCipherSuites();
    }

    @Override
    public Socket createSocket() throws IOException {
        return restringir(delegado.createSocket());
    }

    @Override
    public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
        return restringir(delegado.createSocket(s, host, port, autoClose));
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        return restringir(delegado.createSocket(host, port));
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
        return restringir(delegado.createSocket(host, port, localHost, localPort));
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return restringir(delegado.createSocket(host, port));
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
        return restringir(delegado.createSocket(address, port, localAddress, localPort));
    }
}
