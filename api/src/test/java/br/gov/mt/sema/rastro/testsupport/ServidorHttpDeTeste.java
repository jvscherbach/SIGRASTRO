package br.gov.mt.sema.rastro.testsupport;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Servidor HTTP/1.1 mínimo para os testes das integrações, sobre {@link ServerSocket} bloqueante. Substitui o
 * {@code com.sun.net.httpserver.HttpServer} porque, no JDK 25 em Windows, o HttpServer não consegue abrir o seletor
 * (pipe de loopback por socket de domínio Unix) em algumas máquinas; este servidor não usa seletor. Atende uma
 * requisição por conexão e fecha em seguida. Sem dependência nova.
 */
public final class ServidorHttpDeTeste implements AutoCloseable {

    @FunctionalInterface
    public interface Tratador {
        void tratar(Troca troca) throws IOException;
    }

    /** Uma requisição recebida e a resposta que o teste monta. */
    public static final class Troca {

        private final String metodo;
        private final String caminhoBruto;
        private final Map<String, String> cabecalhos;
        private final OutputStream saida;

        Troca(String metodo, String caminhoBruto, Map<String, String> cabecalhos, OutputStream saida) {
            this.metodo = metodo;
            this.caminhoBruto = caminhoBruto;
            this.cabecalhos = cabecalhos;
            this.saida = saida;
        }

        public String metodo() {
            return metodo;
        }

        /** Caminho como chegou, ainda codificado. */
        public String caminhoBruto() {
            return caminhoBruto;
        }

        /** Valor do cabeçalho da requisição ou nulo; o nome não diferencia maiúsculas. */
        public String cabecalho(String nome) {
            return cabecalhos.get(nome.toLowerCase(Locale.ROOT));
        }

        /** Resposta completa com Content-Length (corpo vazio vira Content-Length: 0). */
        public void responder(int status, byte[] corpo) throws IOException {
            cabecalhosDaResposta(status, "Content-Length: " + corpo.length);
            saida.write(corpo);
            saida.flush();
        }

        /** Só os cabeçalhos de uma resposta em partes (chunked); o corpo vai pelo fluxo devolvido. */
        public OutputStream iniciarEmPartes(int status) throws IOException {
            cabecalhosDaResposta(status, "Transfer-Encoding: chunked");
            saida.flush();
            return new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    write(new byte[] {(byte) b}, 0, 1);
                }

                @Override
                public void write(byte[] dados, int inicio, int tamanho) throws IOException {
                    if (tamanho == 0) {
                        return;
                    }
                    saida.write((Integer.toHexString(tamanho) + "\r\n").getBytes(StandardCharsets.US_ASCII));
                    saida.write(dados, inicio, tamanho);
                    saida.write("\r\n".getBytes(StandardCharsets.US_ASCII));
                }

                @Override
                public void flush() throws IOException {
                    saida.flush();
                }

                @Override
                public void close() throws IOException {
                    saida.write("0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                    saida.flush();
                }
            };
        }

        private void cabecalhosDaResposta(int status, String delimitacao) throws IOException {
            String cabecalho = "HTTP/1.1 " + status + " X\r\nContent-Type: application/pdf\r\n" + delimitacao
                    + "\r\nConnection: close\r\n\r\n";
            saida.write(cabecalho.getBytes(StandardCharsets.US_ASCII));
        }
    }

    private final ServerSocket servidor;
    private final List<Socket> conexoes = new CopyOnWriteArrayList<>();
    private volatile Tratador tratador;

    private ServidorHttpDeTeste() throws IOException {
        this.servidor = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        Thread aceitador = new Thread(this::aceitar, "servidor-http-de-teste");
        aceitador.setDaemon(true);
        aceitador.start();
    }

    public static ServidorHttpDeTeste iniciar() throws IOException {
        return new ServidorHttpDeTeste();
    }

    /** Define o que responde a toda requisição; trocar durante o teste vale para as próximas conexões. */
    public void responderCom(Tratador novo) {
        this.tratador = novo;
    }

    public String url() {
        return "http://127.0.0.1:" + servidor.getLocalPort();
    }

    private void aceitar() {
        while (!servidor.isClosed()) {
            try {
                Socket socket = servidor.accept();
                conexoes.add(socket);
                Thread atendimento = new Thread(() -> atender(socket), "atendimento-http-de-teste");
                atendimento.setDaemon(true);
                atendimento.start();
            } catch (IOException e) {
                return;
            }
        }
    }

    private void atender(Socket socket) {
        try (socket) {
            BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
            String linhaInicial = entrada.readLine();
            if (linhaInicial == null) {
                return;
            }
            String[] partes = linhaInicial.split(" ");
            Map<String, String> cabecalhos = new ConcurrentHashMap<>();
            for (String linha = entrada.readLine(); linha != null && !linha.isEmpty(); linha = entrada.readLine()) {
                int doisPontos = linha.indexOf(':');
                if (doisPontos > 0) {
                    cabecalhos.put(linha.substring(0, doisPontos).trim().toLowerCase(Locale.ROOT),
                            linha.substring(doisPontos + 1).trim());
                }
            }
            OutputStream saida = socket.getOutputStream();
            Tratador atual = tratador;
            if (atual == null) {
                new Troca(partes[0], partes[1], cabecalhos, saida).responder(404, new byte[0]);
                return;
            }
            atual.tratar(new Troca(partes[0], partes[1], cabecalhos, saida));
        } catch (IOException e) {
            // o cliente desistiu (tempo limite do teste): nada a fazer
        } finally {
            conexoes.remove(socket);
        }
    }

    @Override
    public void close() {
        try {
            servidor.close();
        } catch (IOException e) {
            // encerrando
        }
        for (Socket socket : conexoes) {
            try {
                socket.close();
            } catch (IOException e) {
                // encerrando
            }
        }
    }

}
