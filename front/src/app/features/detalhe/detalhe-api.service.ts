import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { SEM_TOAST } from '../../core/interceptors';
import { Requerimento } from './detalhe.models';
import { FinanceiroDoRequerimento } from './financeiro.models';
import { PosicaoDoRequerimento } from './posicao.models';
import { TramitacaoDoRequerimento } from './tramitacao.models';

@Injectable({ providedIn: 'root' })
export class DetalheApiService {
  private readonly http = inject(HttpClient);

  obter(id: number): Observable<Requerimento> {
    // A tela mostra o erro no corpo da página (sem toast duplicado).
    return this.http.get<Requerimento>(`/api/requerimentos/${id}`, {
      context: new HttpContext().set(SEM_TOAST, true),
    });
  }

  /** Parâmetros de exibição da tramitação (S7, bloco 10): quantas posições pedir ao mesmo tempo. Sem toast. */
  parametrosDeExibicao(): Observable<{ posicoesSimultaneas: number }> {
    return this.http.get<{ posicoesSimultaneas: number }>('/api/tramitacao/parametros', {
      context: new HttpContext().set(SEM_TOAST, true),
    });
  }

  /** Tramitação completa (S8): caminho percorrido, com quem esteve, voltas e final. Carrega e falha sozinha, sem toast. */
  tramitacao(id: number): Observable<TramitacaoDoRequerimento> {
    return this.http.get<TramitacaoDoRequerimento>(`/api/requerimentos/${id}/tramitacao`, {
      context: new HttpContext().set(SEM_TOAST, true),
    });
  }

  /** Histórico financeiro (S9): taxas, DAR, vencimento, pagamento e isenção. Carrega e falha sozinho, sem toast. */
  financeiro(id: number): Observable<FinanceiroDoRequerimento> {
    return this.http.get<FinanceiroDoRequerimento>(`/api/requerimentos/${id}/financeiro`, {
      context: new HttpContext().set(SEM_TOAST, true),
    });
  }

  /** Posição na tramitação (S7). Carrega e falha sozinha: o erro aparece no próprio bloco, sem toast. */
  posicao(id: number): Observable<PosicaoDoRequerimento> {
    return this.http.get<PosicaoDoRequerimento>(`/api/requerimentos/${id}/posicao`, {
      context: new HttpContext().set(SEM_TOAST, true),
    });
  }
}
