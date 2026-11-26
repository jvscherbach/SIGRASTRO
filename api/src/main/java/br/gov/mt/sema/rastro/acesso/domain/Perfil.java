package br.gov.mt.sema.rastro.acesso.domain;

/** Perfis e permissões conforme a matriz de docs/12-configuracoes.md. */
public enum Perfil {

    OPERADOR("Operador", false, false, false, true, false, false, false),
    OPERADOR_SIGILO("Gestor", true, false, false, false, true, true, false),
    /**
     * Analista (S11): só tem a permissão {@code verCaixa}. Não é restrito ao interessado (a caixa abre o requerimento que
     * está na posse dele, que não é dele como interessado) e não vê sigilosos, auditoria, configurações, responsável da
     * tarefa nem o painel.
     */
    ANALISTA("Analista", false, false, false, false, false, false, true),
    ADMIN("Administrador", true, true, true, false, true, true, false);

    private final String rotulo;
    private final boolean verSigilosos;
    private final boolean verAuditoriaAcessos;
    private final boolean acessarConfiguracoes;
    private final boolean restritoAoInteressado;
    private final boolean verResponsavelTarefa;
    private final boolean verPainel;
    private final boolean verCaixa;

    Perfil(String rotulo, boolean verSigilosos, boolean verAuditoriaAcessos, boolean acessarConfiguracoes,
            boolean restritoAoInteressado, boolean verResponsavelTarefa, boolean verPainel, boolean verCaixa) {
        this.verPainel = verPainel;
        this.verCaixa = verCaixa;
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

    /**
     * A auditoria de documentos (S5) é do Operador (só os requerimentos dele), do Gestor e do Administrador. O Analista
     * (S11) não a vê: como não é restrito ao interessado, veria os eventos de todos (achado da varredura do bloco 10).
     */
    public boolean podeVerAuditoriaDeDocumentos() {
        return this != ANALISTA;
    }

    /** O painel do Gestor (S10) é só de Gestor e Administrador. */
    public boolean podeVerPainel() {
        return verPainel;
    }

    /** A caixa de entrada do analista (S11) é só do Analista; Gestor e Administrador não têm caixa. */
    public boolean podeVerCaixa() {
        return verCaixa;
    }
}
