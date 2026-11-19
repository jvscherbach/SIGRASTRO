package br.gov.mt.sema.rastro.config;

import br.gov.mt.sema.rastro.configuracao.application.ParametrosEmVigor;
import br.gov.mt.sema.rastro.configuracao.domain.CatalogoDeParametros;
import br.gov.mt.sema.rastro.documento.application.port.OrigensLigadas;
import br.gov.mt.sema.rastro.documento.domain.OrigemDocumento;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Padrão em {@code rastro.documentos.origens.<origem>}, com a sobreposição da tela por cima. */
@Component
class OrigensLigadasVigentes implements OrigensLigadas {

    private final Environment ambiente;
    private final ParametrosEmVigor vigentes;

    OrigensLigadasVigentes(Environment ambiente, ParametrosEmVigor vigentes) {
        this.ambiente = ambiente;
        this.vigentes = vigentes;
    }

    @Override
    public boolean ligada(OrigemDocumento origem) {
        boolean doAmbiente = ambiente.getProperty("rastro.documentos.origens." + origem.propriedade(), Boolean.class, true);
        return vigentes.booleano(CatalogoDeParametros.PREFIXO_ORIGEM + origem.propriedade(), doAmbiente);
    }
}
