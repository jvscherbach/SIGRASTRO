import { Injectable, inject } from '@angular/core';
import { Observable, Subscription, finalize } from 'rxjs';

import { DetalheApiService } from './detalhe-api.service';
import { PosicaoDoRequerimento } from './posicao.models';

/**
 * Limite padrão de posições pedidas ao mesmo tempo pela tela (S7, bloco 9; docs/12), usado até o servidor responder.
 * O valor em vigor vem de `GET /api/tramitacao/parametros` (Configurações › Rastro, bloco 10).
 */
export const MAXIMO_DE_POSICOES_SIMULTANEAS = 4;
const LIMITE_MINIMO = 1;
const LIMITE_MAXIMO = 20;

/**
 * Fila das leituras de posição: no máximo `limite` em andamento; as outras começam quando uma termina (com sucesso, erro
 * ou cancelamento). Cancelar antes de começar tira o pedido da fila sem ir ao servidor. Cada pedido continua
 * independente: a falha de um não afeta os demais.
 *
 * O limite é lido do servidor no primeiro pedido de cada carregamento da página (uma alteração nas Configurações vale ao
 * abrir o Rastro de novo); até a resposta, um pedido por vez; se a leitura falhar, o padrão.
 */
@Injectable({ providedIn: 'root' })
export class FilaDePosicoes {
  private readonly api = inject(DetalheApiService);
  private emAndamento = 0;
  private readonly esperando: (() => void)[] = [];
  /**
   * Até o servidor responder, um pedido por vez: assim um limite menor nas Configurações nunca é ultrapassado no
   * primeiro lote. Depois, o valor do servidor; se a leitura falhar, o padrão.
   */
  private limiteAtual = LIMITE_MINIMO;
  private limitePedido = false;

  /** Pedidos em andamento agora (para teste e diagnóstico). */
  get ativos(): number {
    return this.emAndamento;
  }

  /** Limite em vigor. */
  get limite(): number {
    return this.limiteAtual;
  }

  posicao(idRequerimento: number): Observable<PosicaoDoRequerimento> {
    this.lerLimite();
    return new Observable<PosicaoDoRequerimento>((assinante) => {
      let interna: Subscription | null = null;
      let liberado = false;
      const liberar = () => {
        if (!liberado) {
          liberado = true;
          this.emAndamento--;
          this.proximo();
        }
      };
      const iniciar = () => {
        this.emAndamento++;
        interna = this.api.posicao(idRequerimento).pipe(finalize(liberar)).subscribe(assinante);
      };
      if (this.emAndamento < this.limiteAtual) {
        iniciar();
      } else {
        this.esperando.push(iniciar);
      }
      return () => {
        const naFila = this.esperando.indexOf(iniciar);
        if (naFila >= 0) {
          this.esperando.splice(naFila, 1);
        }
        interna?.unsubscribe();
      };
    });
  }

  private lerLimite(): void {
    if (this.limitePedido) {
      return;
    }
    this.limitePedido = true;
    this.api.parametrosDeExibicao().subscribe({
      next: ({ posicoesSimultaneas }) =>
        this.definirLimite(
          Number.isInteger(posicoesSimultaneas)
            ? Math.min(LIMITE_MAXIMO, Math.max(LIMITE_MINIMO, posicoesSimultaneas))
            : MAXIMO_DE_POSICOES_SIMULTANEAS,
        ),
      error: () => this.definirLimite(MAXIMO_DE_POSICOES_SIMULTANEAS),
    });
  }

  private definirLimite(limite: number): void {
    this.limiteAtual = limite;
    this.proximo();
  }

  private proximo(): void {
    while (this.emAndamento < this.limiteAtual && this.esperando.length) {
      this.esperando.shift()!();
    }
  }
}
