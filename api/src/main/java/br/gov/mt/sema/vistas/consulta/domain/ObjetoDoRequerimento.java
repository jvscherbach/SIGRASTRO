package br.gov.mt.sema.vistas.consulta.domain;

import java.util.List;

/**
 * Objeto do requerimento (P01). As listas são independentes: não existe pareamento entre objetivo e finalidade,
 * e nunca se faz junção entre elas. Processos de outorga e de licenciamento preenchem blocos diferentes; o que
 * não tem dados fica vazio (ou nulo, no caso da atividade de licenciamento).
 */
public record ObjetoDoRequerimento(List<String> objetivos, List<FinalidadeAtividade> finalidadesAtividades,
        List<TipoObjetivo> tiposObjetivo, AtividadeLicenciamento atividadeLicenciamento) {

    public record FinalidadeAtividade(String finalidade, String atividade) {
    }

    /** Linha do bloco Tipo/Objetivo; {@code tipoDescricao} é o texto de exibição de {@code tipo}. */
    public record TipoObjetivo(String tipo, String tipoDescricao, String objetivo) {

        public static TipoObjetivo de(String tipoDoSiga, String objetivo) {
            return new TipoObjetivo(tipoDoSiga, TipoOutorga.descricaoDe(tipoDoSiga), objetivo);
        }
    }

    public record AtividadeLicenciamento(String grupo, String atividade) {
    }

    public ObjetoDoRequerimento {
        objetivos = List.copyOf(objetivos);
        finalidadesAtividades = List.copyOf(finalidadesAtividades);
        tiposObjetivo = List.copyOf(tiposObjetivo);
    }

    public static ObjetoDoRequerimento vazio() {
        return new ObjetoDoRequerimento(List.of(), List.of(), List.of(), null);
    }
}
