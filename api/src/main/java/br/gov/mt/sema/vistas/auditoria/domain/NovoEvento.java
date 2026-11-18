package br.gov.mt.sema.vistas.auditoria.domain;

import java.util.List;

/**
 * O que um módulo pede para registrar. É só o transporte: montar um {@code NovoEvento} nunca lança; a validação fica em
 * {@link EventoDeAuditoria}, dentro de {@code RegistrarEvento}, que não deixa a falha chegar a quem chamou.
 */
public record NovoEvento(TipoDeEvento tipo, UsuarioDoEvento usuario, Long idRequerimento, String numeroProcesso,
        String numeroRequerimento, VinculoDoInteressado vinculo, boolean sigiloso, String arquivo, String idPedido,
        String origem, String detalhe, String correlationId, List<DocumentoDoEvento> documentos,
        List<AlteracaoDoEvento> alteracoes) {

    public static Construtor de(TipoDeEvento tipo, UsuarioDoEvento usuario) {
        return new Construtor(tipo, usuario);
    }

    /** Cada tipo preenche só o que usa (docs/11). */
    public static final class Construtor {

        private final TipoDeEvento tipo;
        private final UsuarioDoEvento usuario;
        private Long idRequerimento;
        private String numeroProcesso;
        private String numeroRequerimento;
        private VinculoDoInteressado vinculo;
        private boolean sigiloso;
        private String arquivo;
        private String idPedido;
        private String origem;
        private String detalhe;
        private String correlationId;
        private List<DocumentoDoEvento> documentos = List.of();
        private List<AlteracaoDoEvento> alteracoes = List.of();

        private Construtor(TipoDeEvento tipo, UsuarioDoEvento usuario) {
            this.tipo = tipo;
            this.usuario = usuario;
        }

        public Construtor requerimento(Long id, String numeroProcesso, String numeroRequerimento) {
            this.idRequerimento = id;
            this.numeroProcesso = numeroProcesso;
            this.numeroRequerimento = numeroRequerimento;
            return this;
        }

        /** O CPF/CNPJ do interessado do requerimento, em qualquer formato. */
        public Construtor interessado(String cpfCnpj) {
            this.vinculo = VinculoDoInteressado.de(cpfCnpj);
            return this;
        }

        public Construtor sigiloso(boolean sigiloso) {
            this.sigiloso = sigiloso;
            return this;
        }

        public Construtor arquivo(String arquivo) {
            this.arquivo = arquivo;
            return this;
        }

        public Construtor pedido(String idPedido) {
            this.idPedido = idPedido;
            return this;
        }

        public Construtor origem(String origem) {
            this.origem = origem;
            return this;
        }

        public Construtor detalhe(String detalhe) {
            this.detalhe = detalhe;
            return this;
        }

        public Construtor correlacao(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Construtor documentos(List<DocumentoDoEvento> documentos) {
            this.documentos = documentos;
            return this;
        }

        public Construtor alteracoes(List<AlteracaoDoEvento> alteracoes) {
            this.alteracoes = alteracoes;
            return this;
        }

        public NovoEvento criar() {
            return new NovoEvento(tipo, usuario, idRequerimento, numeroProcesso, numeroRequerimento, vinculo, sigiloso,
                    arquivo, idPedido, origem, detalhe, correlationId, documentos, alteracoes);
        }
    }
}
