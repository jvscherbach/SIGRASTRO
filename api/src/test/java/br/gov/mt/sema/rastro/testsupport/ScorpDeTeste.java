package br.gov.mt.sema.rastro.testsupport;

import java.util.List;
import java.util.Map;
import java.util.Set;

import br.gov.mt.sema.rastro.documento.application.DocumentosPessoaisDoRequerimento;
import br.gov.mt.sema.rastro.documento.application.port.DocumentosPessoais;

/** SCORP desligado para os testes de caso de uso que não tratam de documentos pessoais. */
public final class ScorpDeTeste {

    private ScorpDeTeste() {
    }

    public static DocumentosPessoaisDoRequerimento desligado() {
        DocumentosPessoais scorp = new DocumentosPessoais() {
            @Override
            public boolean habilitado() {
                return false;
            }

            @Override
            public Consulta consultar(Set<String> idsCorporativos) {
                return new Consulta(Map.of(), Set.of());
            }
        };
        return new DocumentosPessoaisDoRequerimento(id -> List.of(), scorp, () -> false);
    }
}
