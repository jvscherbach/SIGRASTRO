package br.gov.mt.sema.vistas.auditoria.domain;

/** Tipos de evento de docs/11. O grupo decorre do tipo. */
public enum TipoDeEvento {

    CONSULTA(GrupoDeEvento.DOCUMENTOS),
    VISUALIZACAO(GrupoDeEvento.DOCUMENTOS),
    DOSSIE(GrupoDeEvento.DOCUMENTOS),
    FALHA(GrupoDeEvento.DOCUMENTOS),
    DOWNLOAD(GrupoDeEvento.DOCUMENTOS),
    GEOMETRIAS(GrupoDeEvento.DOCUMENTOS),
    LOGIN(GrupoDeEvento.ACESSOS),
    PERFIL(GrupoDeEvento.ACESSOS),
    USUARIO(GrupoDeEvento.ACESSOS),
    CONFIG(GrupoDeEvento.ACESSOS);

    private final GrupoDeEvento grupo;

    TipoDeEvento(GrupoDeEvento grupo) {
        this.grupo = grupo;
    }

    public GrupoDeEvento grupo() {
        return grupo;
    }
}
