package br.gov.mt.sema.rastro.auditoria.application;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import br.gov.mt.sema.rastro.acesso.application.PoliticaDeAcesso;
import br.gov.mt.sema.rastro.auditoria.application.port.ConsultarEventos;
import br.gov.mt.sema.rastro.auditoria.application.port.ParametrosDaAuditoria;
import br.gov.mt.sema.rastro.auditoria.domain.EventoDeAuditoria;
import br.gov.mt.sema.rastro.auditoria.domain.GrupoDeEvento;
import br.gov.mt.sema.rastro.auditoria.domain.TipoDeEvento;
import br.gov.mt.sema.rastro.shared.application.exception.ParametroInvalidoException;
import br.gov.mt.sema.rastro.shared.application.exception.RecursoNaoEncontradoException;
import org.springframework.stereotype.Service;

/**
 * Lista, detalhe e CSV da auditoria (RF21 a RF24). As regras são as mesmas nos três: acessos só para quem a política
 * deixar; eventos só do interessado que a política liberar (RN07); documento sigiloso mascarado para quem não o acessa.
 * O mascaramento vem <b>antes</b> de qualquer filtro: a busca por texto só enxerga o que o usuário pode ler, então não
 * revela documento sigiloso por correspondência.
 */
@Service
public class ConsultarAuditoria {

    static final int TAMANHO_PADRAO = 20;
    static final int TAMANHO_MAXIMO = 100;
    static final String DETALHE_OMITIDO = "Detalhe omitido: documento sigiloso.";
    private static final DateTimeFormatter DATA_DO_ARQUIVO = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ConsultarEventos eventos;
    private final PoliticaDeAcesso politica;
    private final ParametrosDaAuditoria parametros;
    private final Clock relogio;

    public ConsultarAuditoria(ConsultarEventos eventos, PoliticaDeAcesso politica, ParametrosDaAuditoria parametros,
            Clock relogio) {
        this.eventos = eventos;
        this.politica = politica;
        this.parametros = parametros;
        this.relogio = relogio;
    }

    public PaginaDeEventos listar(GrupoDeEvento grupo, FiltroDeEventos filtro, Integer pagina, Integer tamanho) {
        Visao visao = visao(grupo);
        int numeroDaPagina = pagina == null ? 0 : pagina;
        int tamanhoDaPagina = tamanho == null ? TAMANHO_PADRAO : tamanho;
        if (numeroDaPagina < 0) {
            throw new ParametroInvalidoException("pagina", "A página começa em 0.");
        }
        if (tamanhoDaPagina < 1 || tamanhoDaPagina > TAMANHO_MAXIMO) {
            throw new ParametroInvalidoException("tamanho", "O tamanho vai de 1 a " + TAMANHO_MAXIMO + ".");
        }
        Predicate<EventoDeAuditoria> filtros = filtros(grupo, filtro, visao);
        long inicio = (long) numeroDaPagina * tamanhoDaPagina;
        Contagem contagem = new Contagem();
        List<EventoDeAuditoria> itens = new ArrayList<>();
        long total = 0;
        for (EventoDeAuditoria evento : (Iterable<EventoDeAuditoria>) doPeriodo(grupo, filtro, visao)::iterator) {
            contagem.somar(evento);
            if (filtros.test(evento)) {
                if (total >= inicio && itens.size() < tamanhoDaPagina) {
                    itens.add(evento);
                }
                total++;
            }
        }
        return new PaginaDeEventos(itens, numeroDaPagina, tamanhoDaPagina, total, contagem.indicadores());
    }

    /** Evento fora do que o usuário vê é 404, como o que não existe. */
    public EventoDeAuditoria detalhar(GrupoDeEvento grupo, long id) {
        Visao visao = visao(grupo);
        return eventos.porId(grupo, id).filter(visao::mostra).map(visao::mascarar)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Evento não encontrado."));
    }

    /** Mesmas regras e filtros da lista, sem paginar; a checagem vem antes de qualquer byte da resposta. */
    public ExportacaoDeEventos exportar(GrupoDeEvento grupo, FiltroDeEventos filtro) {
        Visao visao = visao(grupo);
        Predicate<EventoDeAuditoria> filtros = filtros(grupo, filtro, visao);
        int limite = parametros.maximoDeLinhasDoCsv();
        boolean truncado = doPeriodo(grupo, filtro, visao).filter(filtros).limit(limite + 1L).count() > limite;
        Stream<String> linhas = doPeriodo(grupo, filtro, visao).filter(filtros).limit(limite).map(CsvDeEventos::linha);
        String nome = "auditoria-" + grupo.name().toLowerCase(Locale.ROOT) + "-"
                + DATA_DO_ARQUIVO.format(agora()) + ".csv";
        return new ExportacaoDeEventos(nome, truncado, CsvDeEventos.cabecalho(grupo), linhas);
    }

    private Visao visao(GrupoDeEvento grupo) {
        if (grupo == GrupoDeEvento.ACESSOS) {
            politica.exigirAuditoriaDeAcessos();
        } else {
            politica.exigirAuditoriaDeDocumentos();
        }
        return new Visao(politica.regraDoInteressadoDoUsuarioAtual(), politica.podeVerDocumentoSigiloso());
    }

    private Stream<EventoDeAuditoria> doPeriodo(GrupoDeEvento grupo, FiltroDeEventos filtro, Visao visao) {
        return eventos.doPeriodo(grupo, inicioDoPeriodo(filtro.periodo()), null).filter(visao::mostra)
                .map(visao::mascarar);
    }

    private Instant inicioDoPeriodo(String periodo) {
        ZonedDateTime agora = agora();
        return switch (periodo == null || periodo.isBlank() ? "7d" : periodo) {
            case "hoje" -> agora.toLocalDate().atStartOfDay(EventoDeAuditoria.FUSO).toInstant();
            case "7d" -> agora.toInstant().minus(Duration.ofDays(7));
            case "30d" -> agora.toInstant().minus(Duration.ofDays(30));
            case "todo" -> null;
            default -> throw new ParametroInvalidoException("periodo", "Use hoje, 7d, 30d ou todo.");
        };
    }

    private ZonedDateTime agora() {
        return relogio.instant().atZone(EventoDeAuditoria.FUSO);
    }

    private static Predicate<EventoDeAuditoria> filtros(GrupoDeEvento grupo, FiltroDeEventos filtro, Visao visao) {
        if (filtro.somenteSigilosos() && (grupo != GrupoDeEvento.DOCUMENTOS || !visao.veSigilosos())) {
            throw new ParametroInvalidoException("somenteSigilosos", "Filtro indisponível para esta consulta.");
        }
        TipoDeEvento tipo = tipo(grupo, filtro.tipo());
        String usuario = filtro.usuario() == null || filtro.usuario().isBlank() ? null : filtro.usuario().strip();
        String texto = filtro.texto() == null || filtro.texto().isBlank() ? null : normalizar(filtro.texto().strip());
        return evento -> (tipo == null || evento.tipo() == tipo)
                && (usuario == null || usuario.equalsIgnoreCase(evento.usuario().login()))
                && (!filtro.somenteSigilosos() || evento.sigiloso())
                && (texto == null || textoDe(evento).contains(texto));
    }

    private static TipoDeEvento tipo(GrupoDeEvento grupo, String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return null;
        }
        return Stream.of(TipoDeEvento.values()).filter(t -> t.grupo() == grupo && t.name().equals(codigo.strip()))
                .findFirst()
                .orElseThrow(() -> new ParametroInvalidoException("tipo", "Tipo de evento desconhecido."));
    }

    /** Usuário, números, documento, arquivo e código do evento, do evento já mascarado. */
    private static String textoDe(EventoDeAuditoria evento) {
        List<String> partes = new ArrayList<>(List.of(String.valueOf(evento.id()), evento.tipo().name(),
                evento.tipo().rotulo()));
        Stream.of(evento.usuario().login(), evento.usuario().nome(), evento.numeroProcesso(),
                evento.numeroRequerimento(), evento.arquivo(), evento.detalhe()).filter(Objects::nonNull)
                .forEach(partes::add);
        evento.documentos().forEach(d -> {
            partes.add(d.titulo());
            if (d.nomeDoArquivo() != null) {
                partes.add(d.nomeDoArquivo());
            }
        });
        evento.alteracoes().forEach(a -> partes.add(a.item()));
        return normalizar(String.join("\n", partes));
    }

    private static String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    /** O que o usuário da requisição enxerga; lido uma vez, no início da consulta. */
    private record Visao(Predicate<String> regraDoInteressado, boolean veSigilosos) {

        boolean mostra(EventoDeAuditoria evento) {
            return evento.grupo() == GrupoDeEvento.ACESSOS || evento.vinculo().atende(regraDoInteressado);
        }

        EventoDeAuditoria mascarar(EventoDeAuditoria evento) {
            return veSigilosos ? evento : evento.comSigilosoMascarado(PoliticaDeAcesso.TITULO_SIGILOSO, DETALHE_OMITIDO);
        }
    }

    private static final class Contagem {

        private long dossies;
        private long sigilosos;
        private long entradas;
        private long alteracoes;
        private final Set<String> usuarios = new HashSet<>();

        void somar(EventoDeAuditoria evento) {
            usuarios.add(evento.usuario().login());
            sigilosos += evento.sigiloso() ? 1 : 0;
            switch (evento.tipo()) {
                case DOSSIE -> dossies++;
                case LOGIN -> entradas++;
                case USUARIO, CONFIG -> alteracoes++;
                default -> { }
            }
        }

        IndicadoresDoPeriodo indicadores() {
            return new IndicadoresDoPeriodo(dossies, sigilosos, entradas, alteracoes, usuarios.size());
        }
    }
}
