import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { SEM_TOAST } from '../../core/interceptors';
import { AnalistasDoPainelResposta, PainelResposta, RequerimentosDoAnalistaResposta } from './painel.models';

/** A tela mostra o erro no próprio corpo, sem toast duplicado. */
const SEM_AVISO = () => new HttpContext().set(SEM_TOAST, true);

@Injectable({ providedIn: 'root' })
export class PainelApiService {
  private readonly http = inject(HttpClient);

  /** Só lê o instantâneo em memória: nunca consulta o SIGA nem o Camunda. */
  obter(): Observable<PainelResposta> {
    return this.http.get<PainelResposta>('/api/painel', { context: SEM_AVISO() });
  }

  /** Analistas com o total de cada um (o da barra), do instantâneo do painel. */
  analistas(): Observable<AnalistasDoPainelResposta> {
    return this.http.get<AnalistasDoPainelResposta>('/api/painel/analistas', { context: SEM_AVISO() });
  }

  /** Requerimentos de um analista (identificador opaco), com os filtros de etapa e busca. */
  requerimentosDoAnalista(analista: string, etapa: string, busca: string): Observable<RequerimentosDoAnalistaResposta> {
    let params = new HttpParams();
    if (etapa) {
      params = params.set('etapa', etapa);
    }
    if (busca.trim()) {
      params = params.set('busca', busca.trim());
    }
    return this.http.get<RequerimentosDoAnalistaResposta>(
      `/api/painel/analistas/${encodeURIComponent(analista)}/requerimentos`,
      { params, context: SEM_AVISO() },
    );
  }

  /** 202 quando aceita; 429 ATUALIZACAO_RECENTE com `tentarEmSegundos` antes do intervalo mínimo. */
  atualizar(): Observable<unknown> {
    return this.http.post('/api/painel/atualizacao', null, { context: SEM_AVISO() });
  }
}
