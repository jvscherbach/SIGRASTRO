package br.gov.mt.sema.rastro.config;

import br.gov.mt.sema.rastro.auditoria.application.port.ParametrosDaAuditoria;
import br.gov.mt.sema.rastro.configuracao.application.ParametrosEmVigor;
import br.gov.mt.sema.rastro.configuracao.domain.CatalogoDeParametros;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Parâmetros da auditoria vindos de variáveis de ambiente (protótipo). Valor fora da faixa cai no padrão, com aviso no log. */
@Component
class ParametrosDaAuditoriaDoAmbiente implements ParametrosDaAuditoria {

    private static final Logger log = LoggerFactory.getLogger(ParametrosDaAuditoriaDoAmbiente.class);

    static final int MAXIMO_PADRAO = 50_000;
    static final int RETENCAO_DOCUMENTOS_PADRAO = 5;
    static final int RETENCAO_ACESSOS_PADRAO = 1;

    private final int maximoDeEventos;
    private final int maximoDeLinhasDoCsv;
    private final int retencaoDocumentosAnos;
    private final int retencaoAcessosAnos;
    private final ParametrosEmVigor vigentes;

    ParametrosDaAuditoriaDoAmbiente(
            @Value("${rastro.auditoria.max-eventos:50000}") int maximoDeEventos,
            @Value("${rastro.auditoria.retencao-documentos-anos:5}") int retencaoDocumentosAnos,
            @Value("${rastro.auditoria.retencao-acessos-anos:1}") int retencaoAcessosAnos,
            @Value("${rastro.auditoria.csv-max-linhas:50000}") int maximoDeLinhasDoCsv, ParametrosEmVigor vigentes) {
        this.vigentes = vigentes;
        this.maximoDeEventos = positivo("AUDITORIA_MAX_EVENTOS", maximoDeEventos);
        this.maximoDeLinhasDoCsv = positivo("AUDITORIA_CSV_MAX_LINHAS", maximoDeLinhasDoCsv);
        this.retencaoDocumentosAnos = retencao("AUDITORIA_RETENCAO_DOCUMENTOS_ANOS", retencaoDocumentosAnos,
                RETENCAO_DOCUMENTOS_PADRAO);
        this.retencaoAcessosAnos = retencao("AUDITORIA_RETENCAO_ACESSOS_ANOS", retencaoAcessosAnos,
                RETENCAO_ACESSOS_PADRAO);
    }

    private static int positivo(String variavel, int valor) {
        if (valor >= 1) {
            return valor;
        }
        log.warn("{} inválido; usando {}", variavel, MAXIMO_PADRAO);
        return MAXIMO_PADRAO;
    }

    private static int retencao(String variavel, int valor, int padrao) {
        if (RETENCOES_EM_ANOS.contains(valor)) {
            return valor;
        }
        log.warn("{} fora de 1, 2, 5 ou 10; usando {}", variavel, padrao);
        return padrao;
    }

    @Override
    public int maximoDeEventos() {
        return maximoDeEventos;
    }

    @Override
    public int maximoDeLinhasDoCsv() {
        return maximoDeLinhasDoCsv;
    }

    @Override
    public int retencaoDocumentosAnos() {
        return vigentes.inteiro(CatalogoDeParametros.AUDITORIA_RETENCAO_DOCUMENTOS, retencaoDocumentosAnos);
    }

    @Override
    public int retencaoAcessosAnos() {
        return vigentes.inteiro(CatalogoDeParametros.AUDITORIA_RETENCAO_ACESSOS, retencaoAcessosAnos);
    }
}
