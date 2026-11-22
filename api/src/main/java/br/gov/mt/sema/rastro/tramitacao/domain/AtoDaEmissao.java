package br.gov.mt.sema.rastro.tramitacao.domain;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Objects;

/**
 * Um ato dentro da passagem "Emissão do ato" (S8, bloco 3): cada instância filha de emissão (regra técnica 5) é um ato.
 *
 * @param fim vazio = ainda em andamento
 */
public record AtoDaEmissao(Tipo tipo, LocalDateTime inicio, LocalDateTime fim, Estado estado) {

    public AtoDaEmissao {
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(estado, "estado");
    }

    /**
     * Tipo de ato pela chave da instância filha ou pela {@code callActivity} que a chama (levantamento 2; as mesmas chaves do
     * catálogo do Hídrico). Chave desconhecida: {@link #NAO_MAPEADO}, sem erro.
     */
    public enum Tipo {
        AUTORIZACAO("Autorização", "procSurhEmitirAutorizacoes", "taskEmitirAutorizacoes"),
        OUTROS_ATOS("Outros atos", "procSurhEmitirOutrosAtos", "taskEmitirOutrosAtos"),
        CERTIFICADO("Certificado", "procSurhCertificadoCadastro", "taskEmitirCertificadoCadastros"),
        PORTARIA("Portaria", "procSurhEmissaoPortaria", "taskEmitirPortarias"),
        NAO_MAPEADO("Ato não mapeado", null, null);

        private final String rotulo;
        private final String processo;
        private final String chamada;

        Tipo(String rotulo, String processo, String chamada) {
            this.rotulo = rotulo;
            this.processo = processo;
            this.chamada = chamada;
        }

        public String rotulo() {
            return rotulo;
        }

        public static Tipo doProcesso(String chave) {
            return Arrays.stream(values()).filter(t -> t.processo != null && t.processo.equals(chave)).findFirst()
                    .orElse(NAO_MAPEADO);
        }

        public static Tipo daChamada(String idTecnico) {
            return Arrays.stream(values()).filter(t -> t.chamada != null && t.chamada.equals(idTecnico)).findFirst()
                    .orElse(NAO_MAPEADO);
        }
    }

    /**
     * {@code EMITIDO}: a filha terminou normalmente ({@code STATE_ = COMPLETED}); {@code NAO_CONCLUIDO}: terminou de outro
     * jeito (ex.: devolvida, {@code INTERNALLY_TERMINATED}; P40); {@code INDETERMINADO}: só a chamada foi lida, sem a filha.
     */
    public enum Estado { EMITIDO, NAO_CONCLUIDO, EM_ANDAMENTO, INDETERMINADO }

    /** O ato de fato ocorreu (é o que a caixa "Emissão do ato" lista). */
    public boolean ocorreu() {
        return estado == Estado.EMITIDO;
    }
}
