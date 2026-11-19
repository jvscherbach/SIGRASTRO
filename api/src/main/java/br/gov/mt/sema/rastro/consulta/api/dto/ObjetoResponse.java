package br.gov.mt.sema.rastro.consulta.api.dto;

import java.util.List;

import br.gov.mt.sema.rastro.consulta.domain.ObjetoDoRequerimento;

/** Listas independentes: objetivos, pares finalidade/atividade e linhas Tipo/Objetivo não se pareiam entre si. */
public record ObjetoResponse(List<String> objetivos, List<FinalidadeAtividadeResponse> finalidadesAtividades,
        List<TipoObjetivoResponse> tiposObjetivo, AtividadeLicenciamentoResponse atividadeLicenciamento) {

    public record FinalidadeAtividadeResponse(String finalidade, String atividade) {
    }

    public record TipoObjetivoResponse(String tipo, String tipoDescricao, String objetivo) {
    }

    public record AtividadeLicenciamentoResponse(String grupo, String atividade) {
    }

    public static ObjetoResponse de(ObjetoDoRequerimento objeto) {
        ObjetoDoRequerimento.AtividadeLicenciamento a = objeto.atividadeLicenciamento();
        return new ObjetoResponse(objeto.objetivos(),
                objeto.finalidadesAtividades().stream()
                        .map(p -> new FinalidadeAtividadeResponse(p.finalidade(), p.atividade())).toList(),
                objeto.tiposObjetivo().stream()
                        .map(t -> new TipoObjetivoResponse(t.tipo(), t.tipoDescricao(), t.objetivo())).toList(),
                a == null ? null : new AtividadeLicenciamentoResponse(a.grupo(), a.atividade()));
    }
}
