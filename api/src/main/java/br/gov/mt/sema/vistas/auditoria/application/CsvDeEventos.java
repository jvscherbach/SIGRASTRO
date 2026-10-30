package br.gov.mt.sema.vistas.auditoria.application;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import br.gov.mt.sema.vistas.auditoria.domain.AlteracaoDoEvento;
import br.gov.mt.sema.vistas.auditoria.domain.DocumentoDoEvento;
import br.gov.mt.sema.vistas.auditoria.domain.EventoDeAuditoria;
import br.gov.mt.sema.vistas.auditoria.domain.GrupoDeEvento;

/**
 * Formato do CSV (docs/11): separador {@code ;}, células entre aspas, aspas duplicadas, sem quebra de linha dentro da
 * célula. Texto que o Excel leria como fórmula ganha um apóstrofo na frente.
 */
final class CsvDeEventos {

    private static final String SEPARADOR = ";";
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final List<String> COMUNS = List.of("Código", "Data e hora", "Usuário", "Login", "Perfil", "Evento");

    private CsvDeEventos() {
    }

    static String cabecalho(GrupoDeEvento grupo) {
        Stream<String> especificas = grupo == GrupoDeEvento.DOCUMENTOS
                ? Stream.of("Processo", "Requerimento", "Sigiloso", "Arquivo", "Detalhe", "Documentos", "Origem")
                : Stream.of("Detalhe", "Alterações", "Origem");
        return Stream.concat(COMUNS.stream(), especificas).map(CsvDeEventos::celula)
                .collect(Collectors.joining(SEPARADOR));
    }

    static String linha(EventoDeAuditoria evento) {
        Stream<String> comuns = Stream.of(String.valueOf(evento.id()), DATA_HORA.format(evento.dataHora()),
                evento.usuario().nome(), evento.usuario().login(), evento.usuario().perfil(), evento.tipo().rotulo());
        Stream<String> especificas = evento.grupo() == GrupoDeEvento.DOCUMENTOS
                ? Stream.of(evento.numeroProcesso(), evento.numeroRequerimento(), evento.sigiloso() ? "Sim" : "Não",
                        evento.arquivo(), evento.detalhe(), documentos(evento), evento.origem())
                : Stream.of(evento.detalhe(), alteracoes(evento), evento.origem());
        return Stream.concat(comuns, especificas).map(CsvDeEventos::celula).collect(Collectors.joining(SEPARADOR));
    }

    static String celula(String valor) {
        if (valor == null) {
            return "\"\"";
        }
        String visivel = valor.stripLeading();
        boolean formula = !valor.isEmpty() && (inicioDeFormula(valor.charAt(0))
                || (!visivel.isEmpty() && inicioDeFormula(visivel.charAt(0))));
        String seguro = valor.replaceAll("[\\r\\n]+", " ").replace("\"", "\"\"");
        return "\"" + (formula ? "'" : "") + seguro + "\"";
    }

    private static boolean inicioDeFormula(char c) {
        return c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r';
    }

    private static String documentos(EventoDeAuditoria evento) {
        return evento.documentos().stream().map(CsvDeEventos::documento).collect(Collectors.joining(" | "));
    }

    private static String documento(DocumentoDoEvento documento) {
        String nome = documento.nomeDoArquivo() == null ? "" : " (" + documento.nomeDoArquivo() + ")";
        return documento.titulo() + nome;
    }

    private static String alteracoes(EventoDeAuditoria evento) {
        return evento.alteracoes().stream().map(CsvDeEventos::alteracao).collect(Collectors.joining(" | "));
    }

    private static String alteracao(AlteracaoDoEvento alteracao) {
        return alteracao.item() + ": " + alteracao.valorAnterior() + " -> " + alteracao.valorNovo();
    }
}
