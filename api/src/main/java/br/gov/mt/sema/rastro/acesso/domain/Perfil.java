package br.gov.mt.sema.rastro.acesso.domain;

/** Perfis e permissões conforme a matriz de docs/12-configuracoes.md. */
public enum Perfil {

    OPERADOR("Operador", false, false, false, true, false, false),
    OPERADOR_SIGILO("Gestor", true, false, false, false, true, true),
    ADMIN("Administrador", true, true, true, false, true, true);

    private final String rotulo;
    private final boolean verSigilosos;
    private final boolean verAuditoriaAcessos;
    private final boolean acessarConfiguracoes;
    private final boolean restritoAoInteressado;
    private final boolean verResponsavelTarefa;
    private final boolean verPainel;

    Perfil(String rotulo, boolean verSigilosos, boolean verAuditoriaAcessos, boolean acessarConfiguracoes,
            boolean restritoAoInteressado, boolean verResponsavelTarefa, boolean verPainel) {
        this.verPainel = verPainel;
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

    /**
     * Ids técnicos do fluxo (id de atividade do BPMN, chave da filha, ids não mapeados) só para o Administrador (S7):
     * o usuário vê a etapa em linguagem de negócio.
     */
    public boolean podeVerIdsTecnicosDoFluxo() {
        return this == ADMIN;
    }

    /** O nome do responsável pela tarefa atual é visível só para Gestor e Administrador (RN12). */
    public boolean podeVerResponsavelTarefa() {
        return verResponsavelTarefa;
    }

    /** O painel do Gestor (S10) é só de Gestor e Administrador. */
    public boolean podeVerPainel() {
        return verPainel;
    }
}
