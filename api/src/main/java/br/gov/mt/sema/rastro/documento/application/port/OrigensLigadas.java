package br.gov.mt.sema.rastro.documento.application.port;

import br.gov.mt.sema.rastro.documento.domain.OrigemDocumento;

/** Quais origens de documento estão ligadas agora. Origem desligada some da lista e não entra no dossiê (docs/12). */
public interface OrigensLigadas {

    boolean ligada(OrigemDocumento origem);
}
