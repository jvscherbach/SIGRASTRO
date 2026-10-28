import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, input, signal } from '@angular/core';
import { Subscription } from 'rxjs';

import { mensagemDoErro } from '../../core/mensagens-erro';
import { ProblemDetail } from '../../core/usuario.model';
import { baixarBlob, nomeDoContentDisposition } from '../../shared/arquivo-local';
import { formatarData } from '../../shared/formato';
import { IconeComponent } from '../../shared/icone.component';
import { DocumentosApiService } from './documentos-api.service';
import { GeometriaComProblema, GeometriaItem } from './documentos.models';

type Fase = 'parado' | 'baixando' | 'baixado' | 'erro';

/**
 * Seção Geometrias do detalhe (S4, RF15, RN10): SHP, KML e KMZ em lista própria, fora da seleção e do dossiê, com
 * "Baixar geometrias" num ZIP (tudo ou nada). O ZIP vem por Blob, com o token no cabeçalho; o que entra nele e o que o
 * perfil vê são decisões do back-end (RNF10).
 */
@Component({
  selector: 'app-geometrias-secao',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconeComponent],
  templateUrl: './geometrias-secao.component.html',
})
export class GeometriasSecaoComponent {
  private readonly api = inject(DocumentosApiService);
  private assinatura?: Subscription;

  readonly idRequerimento = input.required<number>();
  readonly geometrias = input.required<GeometriaItem[]>();
  /** Sigilosas que o perfil não vê (Operador): só a contagem. */
  readonly omitidas = input(0);

  protected readonly fase = signal<Fase>('parado');
  protected readonly mensagem = signal('');
  protected readonly comProblema = signal<GeometriaComProblema[]>([]);
  protected readonly nomeBaixado = signal('');

  /** Só as vigentes vão para o ZIP; sem nenhuma, o download responderia 404. */
  protected readonly baixaveis = computed(() => this.geometrias().filter((g) => g.situacao === 'VIGENTE').length);

  protected readonly formatarData = formatarData;
  protected readonly formatarTamanho = formatarTamanho;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.assinatura?.unsubscribe());
  }

  protected baixar(): void {
    if (this.fase() === 'baixando') {
      return;
    }
    this.fase.set('baixando');
    this.mensagem.set('');
    this.comProblema.set([]);
    this.assinatura = this.api.baixarGeometrias(this.idRequerimento()).subscribe({
      next: (resposta) => {
        const nome = nomeDoContentDisposition(resposta.headers.get('Content-Disposition')) ?? 'geometrias.zip';
        baixarBlob(resposta.body as Blob, nome);
        this.nomeBaixado.set(nome);
        this.fase.set('baixado');
      },
      error: (erro: unknown) => this.aoFalhar(erro),
    });
  }

  private aoFalhar(erro: unknown): void {
    if (!(erro instanceof HttpErrorResponse)) {
      this.mensagem.set('Não foi possível baixar as geometrias. Tente novamente.');
    } else {
      const problema = erro.error as (ProblemDetail & { arquivos?: GeometriaComProblema[] }) | null;
      this.mensagem.set(mensagemDoErro(erro));
      if (problema?.errorCode === 'GEOMETRIAS_INCOMPLETAS' && Array.isArray(problema.arquivos)) {
        this.comProblema.set(problema.arquivos.map((a) => ({ nome: String(a.nome), motivo: String(a.motivo) })));
      }
    }
    this.fase.set('erro');
  }
}

/** "340 KB", "1,2 MB"; vazio quando o tamanho é desconhecido. */
export function formatarTamanho(bytes: number | null | undefined): string {
  if (bytes === null || bytes === undefined || bytes < 0) {
    return '';
  }
  if (bytes < 1024 * 1024) {
    return `${Math.max(1, Math.round(bytes / 1024))} KB`;
  }
  return `${(bytes / (1024 * 1024)).toLocaleString('pt-BR', { maximumFractionDigits: 1 })} MB`;
}
