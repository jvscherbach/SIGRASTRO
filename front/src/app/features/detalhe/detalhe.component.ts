import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { mensagemDoErro } from '../../core/mensagens-erro';
import { formatarCpfCnpj, formatarData, formatarDataHora } from '../../shared/formato';
import { FolhaSobrepostaComponent } from '../../shared/folha-sobreposta.component';
import { IconeComponent } from '../../shared/icone.component';
import { QuadroTopoComponent } from '../../shared/quadro-topo.component';
import { RecentesService } from '../../shared/recentes.service';
import { SituacaoTagComponent } from '../../shared/situacao-tag.component';
import { TrilhaService } from '../../shared/trilha.service';
import { DocumentosSecaoComponent } from '../documentos/documentos-secao.component';
import { DetalheApiService } from './detalhe-api.service';
import { Requerimento, Responsavel } from './detalhe.models';
import { ObjetoDoRequerimentoComponent } from './objeto-do-requerimento.component';

@Component({
  selector: 'app-detalhe',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    QuadroTopoComponent,
    FolhaSobrepostaComponent,
    IconeComponent,
    SituacaoTagComponent,
    ObjetoDoRequerimentoComponent,
    DocumentosSecaoComponent,
  ],
  templateUrl: './detalhe.component.html',
})
export class DetalheComponent {
  private readonly api = inject(DetalheApiService);
  private readonly trilha = inject(TrilhaService);
  private readonly recentes = inject(RecentesService);
  private readonly router = inject(Router);

  protected readonly requerimento = signal<Requerimento | null>(null);
  protected readonly erro = signal('');
  protected readonly carregando = signal(true);

  protected readonly formatarCpfCnpj = formatarCpfCnpj;
  protected readonly formatarData = formatarData;
  protected readonly formatarDataHora = formatarDataHora;

  protected readonly descricao = computed(() => {
    const r = this.requerimento();
    return [r?.interessado?.nome, r?.tipoProcesso].filter(Boolean).join(' · ');
  });

  protected criteriosComoDica(r: Requerimento): string {
    return r.prioridade.criterios.map((c) => c.descricao).join('; ');
  }

  /** Texto do responsável; a linha só existe quando a API mandou o campo (RN12). */
  protected textoDoResponsavel(responsavel: Responsavel): string {
    switch (responsavel.situacao) {
      case 'ATRIBUIDO':
        return responsavel.nome || 'Responsável não identificado';
      case 'NAO_ATRIBUIDA':
        return 'Não atribuída';
      default:
        return 'Responsável não identificado';
    }
  }

  constructor() {
    const id = Number(inject(ActivatedRoute).snapshot.paramMap.get('id'));
    inject(DestroyRef).onDestroy(() => this.trilha.limpar());

    if (!Number.isSafeInteger(id) || id <= 0) {
      this.carregando.set(false);
      this.erro.set('Requerimento não encontrado.');
      return;
    }
    this.api
      .obter(id)
      .pipe(takeUntilDestroyed())
      .subscribe({
        next: (requerimento) => {
          this.requerimento.set(requerimento);
          this.carregando.set(false);
          const { rotulo, numero } = requerimento.identificacao;
          this.trilha.definir(['Consulta', `${rotulo} ${numero}`]);
          this.recentes.registrar({
            id: requerimento.id,
            rotulo: `${rotulo} ${numero}`,
            numeroRequerimento: requerimento.numeroRequerimento,
            situacao: requerimento.situacao.descricao,
          });
        },
        error: (erro: unknown) => {
          this.carregando.set(false);
          this.erro.set(mensagemDoDetalhe(erro));
        },
      });
  }

  protected voltar(): void {
    void this.router.navigate(['/consulta']);
  }
}

/** Mensagem do erro no corpo da página; usa o errorCode, nunca o detail técnico cru. */
export function mensagemDoDetalhe(erro: unknown): string {
  if (!(erro instanceof HttpErrorResponse)) {
    return 'Não foi possível carregar o requerimento. Tente novamente.';
  }
  return (erro.error as { errorCode?: string } | null)?.errorCode === 'NAO_ENCONTRADO'
    ? 'Requerimento não encontrado.'
    : mensagemDoErro(erro);
}
