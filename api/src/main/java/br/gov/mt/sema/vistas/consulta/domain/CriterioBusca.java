package br.gov.mt.sema.vistas.consulta.domain;

import br.gov.mt.sema.vistas.shared.application.exception.ParametroInvalidoException;

/**
 * Critérios da busca (RF01, RF02). {@code restricaoDigitos} é preenchido pelo caso de uso para perfis restritos
 * ao interessado (RN07) e deve ser aplicado na própria consulta, para que total e paginação já venham filtrados.
 */
public record CriterioBusca(TipoBusca tipo, NumeroRequerimento numero, CpfCnpj cpfCnpj, String nome,
        String restricaoDigitos, int pagina, int tamanho) {

    public static final int TAMANHO_PADRAO = 20;
    public static final int TAMANHO_MAXIMO = 100;
    public static final int NOME_MINIMO = 3;

    public static CriterioBusca de(String tipo, String numero, String cpfCnpj, String nome, Integer pagina,
            Integer tamanho) {
        String numeroInformado = vazioParaNulo(numero);
        String tipoInformado = vazioParaNulo(tipo);
        String cpfInformado = vazioParaNulo(cpfCnpj);
        String nomeInformado = vazioParaNulo(nome);

        if (numeroInformado == null && tipoInformado == null && cpfInformado == null && nomeInformado == null) {
            throw new ParametroInvalidoException("criterio", "Informe ao menos um critério de pesquisa.");
        }
        if (numeroInformado != null && tipoInformado == null) {
            throw new ParametroInvalidoException("tipo", "Informe o tipo: REQUERIMENTO ou PROCESSO.");
        }
        if (tipoInformado != null && numeroInformado == null) {
            throw new ParametroInvalidoException("numero", "Informe o número no formato N/AAAA.");
        }
        if (nomeInformado != null && nomeInformado.length() < NOME_MINIMO) {
            throw new ParametroInvalidoException("nome", "Informe ao menos " + NOME_MINIMO + " caracteres do nome.");
        }
        int paginaFinal = pagina == null ? 0 : pagina;
        int tamanhoFinal = tamanho == null ? TAMANHO_PADRAO : tamanho;
        if (paginaFinal < 0) {
            throw new ParametroInvalidoException("pagina", "A página começa em 0.");
        }
        if (tamanhoFinal < 1 || tamanhoFinal > TAMANHO_MAXIMO) {
            throw new ParametroInvalidoException("tamanho", "O tamanho deve estar entre 1 e " + TAMANHO_MAXIMO + ".");
        }
        return new CriterioBusca(
                tipoInformado == null ? null : tipo(tipoInformado),
                numeroInformado == null ? null : NumeroRequerimento.de(numeroInformado),
                cpfInformado == null ? null : CpfCnpj.de(cpfInformado),
                nomeInformado, null, paginaFinal, tamanhoFinal);
    }

    /** Busca só por número: de requerimento é exata (1 resultado); de processo pode devolver vários (1:N). */
    public boolean apenasNumero() {
        return numero != null && cpfCnpj == null && nome == null;
    }

    public CriterioBusca comRestricao(String digitosDoInteressado) {
        return new CriterioBusca(tipo, numero, cpfCnpj, nome, digitosDoInteressado, pagina, tamanho);
    }

    /** Mesmos filtros, sem a restrição de interessado e só com o primeiro item: serve para saber se existe. */
    public CriterioBusca soParaVerificarExistencia() {
        return new CriterioBusca(tipo, numero, cpfCnpj, nome, null, 0, 1);
    }

    public int inicio() {
        return pagina * tamanho;
    }

    /** Padrão para LIKE com ESCAPE de barra invertida: escapa %, _ e a própria barra do texto digitado. */
    public String nomeComoPadraoLike() {
        String escapado = nome.toUpperCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escapado + "%";
    }

    private static TipoBusca tipo(String texto) {
        try {
            return TipoBusca.valueOf(texto.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ParametroInvalidoException("tipo", "Informe o tipo: REQUERIMENTO ou PROCESSO.");
        }
    }

    private static String vazioParaNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
