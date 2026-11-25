import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { SEM_TOAST } from '../../core/interceptors';
import { PainelResposta } from './painel.models';

/** A tela mostra o erro no próprio corpo, sem toast duplicado. */
const SEM_AVISO = () => new HttpContext().set(SEM_TOAST, true);

@Injectable({ providedIn: 'root' })
export class PainelApiService {
  private readonly http = inject(HttpClient);

  /** Só lê o instantâneo em memória: nunca consulta o SIGA nem o Camunda. */
  obter(): Observable<PainelResposta> {
    return this.http.get<PainelResposta>('/api/painel', { context: SEM_AVISO() });
  }

  /** 202 quando aceita; 429 ATUALIZACAO_RECENTE com `tentarEmSegundos` antes do intervalo mínimo. */
  atualizar(): Observable<unknown> {
    return this.http.post('/api/painel/atualizacao', null, { context: SEM_AVISO() });
  }
}
