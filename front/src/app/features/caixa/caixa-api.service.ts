import { HttpClient, HttpContext, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { SEM_TOAST } from '../../core/interceptors';
import { AvisosResposta, CaixaResposta, FiltroDaCaixa } from './caixa.models';

/** A tela mostra o erro no próprio corpo, sem toast duplicado. */
const SEM_AVISO = () => new HttpContext().set(SEM_TOAST, true);

/** Só lê o instantâneo em memória: nunca consulta o SIGA nem o Camunda. A identidade é a da sessão, nunca de parâmetro. */
@Injectable({ providedIn: 'root' })
export class CaixaApiService {
  private readonly http = inject(HttpClient);

  obter(filtro: FiltroDaCaixa): Observable<CaixaResposta> {
    let params = new HttpParams().set('ordem', filtro.ordem);
    if (filtro.etapa) {
      params = params.set('etapa', filtro.etapa);
    }
    if (filtro.busca.trim()) {
      params = params.set('busca', filtro.busca.trim());
    }
    return this.http.get<CaixaResposta>('/api/caixa', { params, context: SEM_AVISO() });
  }

  /** A lista de mudanças. Não grava o visto: quem grava é `marcarVisto`. */
  avisos(): Observable<AvisosResposta> {
    return this.http.get<AvisosResposta>('/api/caixa/avisos', { context: SEM_AVISO() });
  }

  /** Grava o visto só do conjunto recebido. */
  marcarVisto(ids: number[]): Observable<void> {
    return this.http.post<void>('/api/caixa/avisos/visto', { ids }, { context: SEM_AVISO() });
  }

  /** 202 quando aceita; 429 ATUALIZACAO_RECENTE com `tentarEmSegundos`. */
  atualizar(): Observable<unknown> {
    return this.http.post('/api/caixa/atualizar', null, { context: SEM_AVISO() });
  }
}
