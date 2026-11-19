package br.gov.mt.sema.rastro.dossie.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/** Conteúdo da capa (RF12), já em texto de exibição. A quantidade de documentos vem do pedido. */
public record DadosDaCapa(String rotulo, String numero, String interessado, String cpfCnpj, String tipoProcesso,
        String situacao, LocalDateTime abertura, List<String> objetivos, List<String> atividades,
        LocalDateTime geradoEm, String usuario) {

    public DadosDaCapa {
        objetivos = List.copyOf(objetivos);
        atividades = List.copyOf(atividades);
    }

    /** "Dossiê do Requerimento N/AAAA" ou "Dossiê do Processo N/AAAA" (RN04). */
    public String titulo() {
        return "Dossiê do " + rotulo + " " + numero;
    }

    /** RF18: dossie-processo-2825-2025.pdf ou dossie-requerimento-3331-2025.pdf. */
    public String nomeDoArquivo() {
        String numeroSeguro = numero.replaceAll("[^0-9A-Za-z]+", "-").replaceAll("^-+|-+$", "");
        return "dossie-" + rotulo.toLowerCase(Locale.ROOT) + "-" + numeroSeguro + ".pdf";
    }
}
