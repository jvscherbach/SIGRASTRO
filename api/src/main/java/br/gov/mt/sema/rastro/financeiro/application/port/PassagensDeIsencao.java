package br.gov.mt.sema.rastro.financeiro.application.port;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Entradas das passagens "Isenção confirmada" do requerimento na tramitação (S9, bloco 4), o elo {@code FLUXO} da cadeia de
 * apoio da data do "Isento de Taxa". Só caminho <b>exato</b> do Camunda: plano B (aproximado), tramitação indisponível,
 * tipo não suportado ou qualquer falha devolvem lista vazia, nunca exceção (o financeiro não depende do Camunda).
 * O acesso ao requerimento já foi conferido por quem chama.
 */
public interface PassagensDeIsencao {

    List<LocalDateTime> entradas(long idRequerimento);
}
