package br.gov.mt.sema.rastro.acesso.domain;

/** Perfis e permissões conforme a matriz de docs/12-configuracoes.md. */
public enum Perfil {

    OPERADOR("Operador", false, false, false, true, false),
    OPERADOR_SIGILO("Gestor", true, false, false, false, true),
    ADMIN("Administrador", true, true, true, false, true);

    private final String rotulo;
    private final boolean verSigilosos;
    private final boolean verAuditoriaAcessos;
    private final boolean acessarConfiguracoes;
    private final boolean restritoAoInteressado;
    private final boolean verResponsavelTarefa;

    Perfil(String rotulo, boolean verSigilosos, boolean verAuditoriaAcessos, boolean acessarConfiguracoes,
            boolean restritoAoInteressado, boolean verResponsavelTarefa) {
        this.rotulo = rotulo;
        this.verSigilosos = verSigilosos;
        this.verAuditoriaAcessos = verAuditoriaAcessos;
        this.acessarConfiguracoes = acessarConfiguracoes;
        this.restritoAoInteressado = restritoAoInteressado;
        this.verResponsavelTarefa = verResponsavelTarefa;
    }

    public String rotulo() {
        return rotulo;
    }

    public boolean podeVerSigilosos() {
        return verSigilosos;
    }

    public boolean podeVerAuditoriaAcessos() {
        return verAuditoriaAcessos;
    }

    public boolean podeAcessarConfiguracoes() {
        return acessarConfiguracoes;
    }

    /** Operador só acessa processos e requerimentos em que é o interessado (RN07). */
    public boolean restritoAoInteressado() {
        return restritoAoInteressado;
    }

    /** O nome do responsável pela tarefa atual é visível só para Gestor e Administrador (RN12). */
    public boolean podeVerResponsavelTarefa() {
        return verResponsavelTarefa;
    }
}
