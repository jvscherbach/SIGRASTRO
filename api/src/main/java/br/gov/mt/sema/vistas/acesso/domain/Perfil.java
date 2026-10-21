package br.gov.mt.sema.vistas.acesso.domain;

/** Perfis e permissões conforme a matriz de docs/12-configuracoes.md. */
public enum Perfil {

    OPERADOR("Operador", false, false, false, true),
    OPERADOR_SIGILO("Gestor", true, false, false, false),
    ADMIN("Administrador", true, true, true, false);

    private final String rotulo;
    private final boolean verSigilosos;
    private final boolean verAuditoriaAcessos;
    private final boolean acessarConfiguracoes;
    private final boolean restritoAoInteressado;

    Perfil(String rotulo, boolean verSigilosos, boolean verAuditoriaAcessos, boolean acessarConfiguracoes,
            boolean restritoAoInteressado) {
        this.rotulo = rotulo;
        this.verSigilosos = verSigilosos;
        this.verAuditoriaAcessos = verAuditoriaAcessos;
        this.acessarConfiguracoes = acessarConfiguracoes;
        this.restritoAoInteressado = restritoAoInteressado;
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
}
