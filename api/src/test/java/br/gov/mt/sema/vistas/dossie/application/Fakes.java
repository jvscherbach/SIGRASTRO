package br.gov.mt.sema.vistas.dossie.application;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import br.gov.mt.sema.vistas.dossie.application.port.ArquivosDoRepositorio;
import br.gov.mt.sema.vistas.dossie.application.port.EspacoEmDisco;
import br.gov.mt.sema.vistas.dossie.application.port.FalhaNoDocumentoException;
import br.gov.mt.sema.vistas.dossie.application.port.FilaDeGeracao;
import br.gov.mt.sema.vistas.dossie.application.port.MontadorDoDossie;
import br.gov.mt.sema.vistas.dossie.application.port.ParametrosDoDossie;
import br.gov.mt.sema.vistas.dossie.application.port.RepositorioDePedidos;
import br.gov.mt.sema.vistas.dossie.application.port.ValidadorDePdf;
import br.gov.mt.sema.vistas.dossie.domain.DadosDaCapa;
import br.gov.mt.sema.vistas.dossie.domain.ItemDoPedido;
import br.gov.mt.sema.vistas.dossie.domain.PedidoDeDossie;

/** Portas falsas dos testes do dossiê. Os "PDFs" são texto: o que importa aqui é o fluxo, não o PDF. */
final class Fakes {

    private Fakes() {
    }

    /** Relógio que o teste avança à mão. */
    static final class RelogioControlado extends Clock {
        private Instant agora = Instant.parse("2026-10-21T15:00:00Z");

        void avancarMinutos(long minutos) {
            agora = agora.plusSeconds(minutos * 60);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }

    static final class Parametros implements ParametrosDoDossie {
        int ttl = 30;
        int threads = 2;
        boolean sumario = true;
        boolean marcadores = true;
        boolean numeracao = true;
        long espacoMinimoMb = 2048;
        final Path diretorio;

        Parametros(Path diretorio) {
            this.diretorio = diretorio;
        }

        @Override
        public int ttlMinutos() {
            return ttl;
        }

        @Override
        public int threads() {
            return threads;
        }

        @Override
        public int downloadTotalMaximoSegundos() {
            return 900;
        }

        @Override
        public long espacoMinimoMb() {
            return espacoMinimoMb;
        }

        @Override
        public int downloadTimeoutSegundos() {
            return 60;
        }

        @Override
        public boolean sumario() {
            return sumario;
        }

        @Override
        public boolean marcadores() {
            return marcadores;
        }

        @Override
        public boolean numeracao() {
            return numeracao;
        }

        @Override
        public Path diretorio() {
            return diretorio;
        }
    }

    static final class Repositorio implements RepositorioDePedidos {
        final Map<String, PedidoDeDossie> pedidos = new ConcurrentHashMap<>();

        @Override
        public void salvar(PedidoDeDossie pedido) {
            pedidos.put(pedido.id(), pedido);
        }

        @Override
        public Optional<PedidoDeDossie> buscar(String id) {
            return Optional.ofNullable(pedidos.get(id));
        }

        @Override
        public List<PedidoDeDossie> todos() {
            return List.copyOf(pedidos.values());
        }

        @Override
        public void remover(String id) {
            pedidos.remove(id);
        }
    }

    /** Guarda o que foi pedido; cada chave pode ter um comportamento próprio (padrão: grava "paginas=1"). */
    static final class Arquivos implements ArquivosDoRepositorio {
        final List<String> baixadas = new ArrayList<>();
        final Map<String, Consumer<Path>> comportamentos = new HashMap<>();
        Consumer<String> aoBaixar = chave -> { };
        /** Tentativas que o repositório informa por chave (padrão: 1). */
        final Map<String, Integer> tentativas = new HashMap<>();

        @Override
        public int baixar(String chave, Path destino) {
            baixadas.add(chave);
            aoBaixar.accept(chave);
            comportamentos.getOrDefault(chave, Arquivos::paginas1).accept(destino);
            return tentativas.getOrDefault(chave, 1);
        }

        static void paginas1(Path destino) {
            gravar(destino, "paginas=1");
        }

        static Consumer<Path> paginas(int n) {
            return destino -> gravar(destino, "paginas=" + n);
        }

        static Consumer<Path> falhaDoDocumento(String motivo) {
            return destino -> {
                throw new FalhaNoDocumentoException(motivo);
            };
        }
    }

    /** Espaço livre controlado pelo teste (padrão: muito espaço); guarda os diretórios consultados. */
    static final class Espaco implements EspacoEmDisco {
        long livre = Long.MAX_VALUE;
        final List<Path> consultas = new ArrayList<>();

        @Override
        public long livreEmBytes(Path diretorio) {
            consultas.add(diretorio);
            return livre;
        }
    }

    /** Lê "paginas=N"; "invalido" vira falha do documento. */
    static final class Validador implements ValidadorDePdf {
        @Override
        public int validar(Path arquivo) {
            String conteudo = ler(arquivo);
            if (conteudo.startsWith("invalido:")) {
                throw new FalhaNoDocumentoException(conteudo.substring("invalido:".length()));
            }
            return Integer.parseInt(conteudo.substring("paginas=".length()));
        }
    }

    static class Montador implements MontadorDoDossie {
        final List<List<String>> titulosMontados = new ArrayList<>();
        final List<Inventario> inventarios = new ArrayList<>();
        RuntimeException falha;

        @Override
        public int montar(DadosDaCapa capa, List<DocumentoParaMontagem> documentos, Inventario inventario,
                OpcoesDeMontagem opcoes, Path destino) {
            if (falha != null) {
                throw falha;
            }
            inventarios.add(inventario);
            titulosMontados.add(documentos.stream().map(DocumentoParaMontagem::titulo).toList());
            gravar(destino, "DOSSIE " + capa.titulo());
            return 1 + documentos.stream().mapToInt(DocumentoParaMontagem::paginas).sum();
        }
    }

    /** Executa na hora, na própria thread (ou só guarda, se {@code adiar}). */
    static final class FilaSincrona implements FilaDeGeracao {
        final List<Runnable> pendentes = new ArrayList<>();
        boolean adiar;

        @Override
        public void submeter(Runnable geracao) {
            if (adiar) {
                pendentes.add(geracao);
            } else {
                geracao.run();
            }
        }
    }

    static DadosDaCapa capa() {
        return new DadosDaCapa("Processo", "2825/2025", "Associação Residencial Vale Verde", "12.345.678/0001-90",
                "Uso de Recursos Hídricos", "Em análise", LocalDateTime.of(2025, 3, 31, 10, 12), List.of("Consumo humano"),
                List.of("Captação"), LocalDateTime.of(2026, 10, 21, 11, 0), "Gestor Demo");
    }

    static ItemDoPedido item(String id, String titulo, String chave) {
        return new ItemDoPedido("DOCUMENTO_PROCESSO", "Documento do processo", id, titulo,
                LocalDateTime.of(2025, 4, 1, 10, 0), chave);
    }

    static PedidoDeDossie pedido(String login, LocalDateTime agora, int ttl, ItemDoPedido... itens) {
        return new PedidoDeDossie(java.util.UUID.randomUUID().toString(), login, 101, capa(), List.of(itens), agora,
                agora.plusMinutes(ttl));
    }

    static void gravar(Path destino, String conteudo) {
        try {
            Files.writeString(destino, conteudo, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String ler(Path arquivo) {
        try {
            return Files.readString(arquivo, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
