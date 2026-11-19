package br.gov.mt.sema.rastro.auditoria.domain;

/** Tipos de evento de docs/11. O grupo decorre do tipo. */
public enum TipoDeEvento {

    CONSULTA(GrupoDeEvento.DOCUMENTOS, "Consulta"),
    VISUALIZACAO(GrupoDeEvento.DOCUMENTOS, "Visualização"),
    DOSSIE(GrupoDeEvento.DOCUMENTOS, "Dossiê gerado"),
    FALHA(GrupoDeEvento.DOCUMENTOS, "Falha na geração"),
    DOWNLOAD(GrupoDeEvento.DOCUMENTOS, "Download"),
    GEOMETRIAS(GrupoDeEvento.DOCUMENTOS, "Geometrias baixadas"),
    LOGIN(GrupoDeEvento.ACESSOS, "Acesso"),
    PERFIL(GrupoDeEvento.ACESSOS, "Troca de perfil"),
    USUARIO(GrupoDeEvento.ACESSOS, "Usuário alterado"),
    CONFIG(GrupoDeEvento.ACESSOS, "Configuração alterada");

    private final GrupoDeEvento grupo;
    private final String rotulo;

    TipoDeEvento(GrupoDeEvento grupo, String rotulo) {
        this.grupo = grupo;
        this.rotulo = rotulo;
    }

    public GrupoDeEvento grupo() {
        return grupo;
    }

    public String rotulo() {
        return rotulo;
    }
}
