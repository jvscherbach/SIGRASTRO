package br.gov.mt.sema.rastro.tramitacao.domain;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Porta do catálogo de etapas simplificadas (docs/15). O conteúdo vem de arquivo versionado, validado na subida; aqui só a
 * consulta, que nunca lança: modelo desconhecido é "não suportado", id desconhecido é "não mapeada".
 */
public interface CatalogoDeEtapas {

    List<ModeloDeFluxo> modelos();

    /** Modelo pela chave de definição do Camunda ({@code procOutorgaAguaSupSub}, {@code procLicenciamentoLac}). */
    Optional<ModeloDeFluxo> modelo(String chave);

    /** Modelo pelo valor de {@code TIPO_PROCESSO.INSTANCIA_REQUERIMENTO} ({@code RECURSO_HIDRICO}, {@code LICENCIAMENTO_ADESAO}). */
    Optional<ModeloDeFluxo> modeloDaInstancia(String instanciaDoRequerimento);

    /**
     * Identifica o conteúdo carregado (resumo dos arquivos). Entra na chave do cache da posição (bloco 4), para que uma
     * mudança no catálogo não sirva posição resolvida pelo conteúdo antigo.
     */
    String versao();

    default ResolucaoDeEtapa resolver(String chave, String idTecnico, Set<String> percorridos) {
        return modelo(chave)
                .map(m -> m.resolver(idTecnico, percorridos))
                .orElseGet(() -> new ResolucaoDeEtapa.NaoSuportado(chave == null ? "" : chave));
    }
}
