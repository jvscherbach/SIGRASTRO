import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, OnInit, inject, input, output, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';

import { mensagemDoErro } from '../../core/mensagens-erro';
import { formatarDataHora } from '../../shared/formato';
import { IconeComponent } from '../../shared/icone.component';
import { AuditoriaApiService } from './auditoria-api.service';
import { EventoDetalhado, GrupoDeAuditoria, ROTULO_DO_PERFIL } from './auditoria.models';

/**
 * Gaveta de detalhe do evento (RF22): todos os campos permitidos, a origem da requisição, a lista de documentos com o
 * sigilo de cada um e, em usuário e configuração, o antes e depois. O que cada perfil vê já vem decidido pelo servidor
 * (documento sigiloso mascarado; vínculo do interessado e chave do arquivo nunca chegam aqui). Fecha com Esc e clique fora.
 */
@Component({
  selector: 'app-auditoria-detalhe',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconeComponent],
  templateUrl: './auditoria-detalhe.component.html',
  host: { '(document:keydown.escape)': 'fechar.emit()' },
})
export class AuditoriaDetalheComponent implements OnInit {
  private readonly api = inject(AuditoriaApiService);
  private readonly anfitriao = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;

  readonly grupo = input.required<GrupoDeAuditoria>();
  readonly idDoEvento = input.required<number>();
  readonly fechar = output<void>();

  protected readonly carregando = signal(true);
  protected readonly erro = signal('');
  protected readonly evento = signal<EventoDetalhado | null>(null);
  protected readonly formatarDataHora = formatarDataHora;
  protected readonly perfis = ROTULO_DO_PERFIL;

  private assinatura: Subscription | null = null;

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      this.assinatura?.unsubscribe();
      this.anfitriao.remove();
    });
  }

  ngOnInit(): void {
    // Como a gaveta do dossiê: sai da folha da tela e fica acima da barra do topo.
    document.body.appendChild(this.anfitriao);
    this.carregar();
  }

  protected carregar(): void {
    this.assinatura?.unsubscribe();
    this.carregando.set(true);
    this.erro.set('');
    this.assinatura = this.api.detalhe(this.grupo(), this.idDoEvento()).subscribe({
      next: (evento) => {
        this.evento.set(evento);
        this.carregando.set(false);
      },
      error: (erro: unknown) => {
        this.carregando.set(false);
        this.erro.set(erro instanceof HttpErrorResponse ? mensagemDoErro(erro) : 'Não foi possível carregar o evento.');
      },
    });
  }

  protected aoClicarNoFundo(evento: MouseEvent): void {
    if (evento.target === evento.currentTarget) {
      this.fechar.emit();
    }
  }
}
