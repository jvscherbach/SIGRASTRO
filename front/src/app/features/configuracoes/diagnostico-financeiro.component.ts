import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { Subscription } from 'rxjs';

import { mensagemDoErro } from '../../core/mensagens-erro';
import { IconeComponent } from '../../shared/icone.component';
import { DetalheApiService } from '../detalhe/detalhe-api.service';
import { DiagnosticoDoFinanceiro, diagnosticoDe } from './diagnostico-financeiro.models';

/**
 * Diagnóstico do financeiro, na aba Rastro das Configurações (S9, bloco 10): só o Administrador chega aqui (a tela de
 * Configurações é dele, RN08). Somente leitura e sem dado pessoal: informe o identificador do requerimento (o número do
 * endereço da tela de detalhe) e veja **quantos lançamentos foram ignorados, por motivo**, e **de onde veio a data** de
 * cada "Isento de Taxa". Reaproveita a rota do financeiro (a regra de acesso e o evento de auditoria `FINANCEIRO` são os
 * mesmos de quem abre o requerimento); nunca mostra id de lançamento, DAR, valor nem CPF.
 */
@Component({
  selector: 'app-diagnostico-financeiro',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconeComponent],
  template: `
    <section class="diag" aria-labelledby="diag-fin-titulo">
      <h3 id="diag-fin-titulo" class="aud-sub">Diagnóstico do financeiro</h3>
      <p class="nota-historico">
        Somente leitura. Mostra quantos lançamentos de taxa a regra de validade ignorou, por motivo, e de onde veio a data do
        "Isento de Taxa", para um requerimento. Não mostra valores, números de DAR nem dados pessoais.
      </p>
      <form class="diag-form" novalidate (submit)="$event.preventDefault(); consultar()">
        <div class="field">
          <label for="diag-fin-id">Identificador do requerimento</label>
          <input id="diag-fin-id" type="text" inputmode="numeric" autocomplete="off" maxlength="12" [value]="id()"
            [attr.aria-invalid]="!!aviso()" aria-describedby="diag-fin-ajuda" (input)="digitar($event)" />
          <small id="diag-fin-ajuda" class="muted">O número que aparece no endereço da tela de detalhe do requerimento.</small>
        </div>
        <button type="submit" class="btn sm" [disabled]="carregando()">Consultar</button>
      </form>

      @if (aviso(); as a) {
        <div class="alert cfg-erro" role="alert"><app-icone nome="alert" /><span>{{ a }}</span></div>
      }
      @if (carregando()) {
        <p class="muted" role="status">Consultando…</p>
      }
      @if (diagnostico(); as d) {
        <div class="diag-resultado" aria-live="polite">
          <p class="diag-titulo"><b>Requerimento {{ d.numeroRequerimento }}</b></p>
          <dl class="diag-lista">
            <div><dt>Lançamentos mantidos</dt><dd>{{ d.validos }}</dd></div>
            <div><dt>Mantidos com dados incompletos</dt><dd>{{ d.inconsistentes }}</dd></div>
          </dl>

          <h4 class="diag-sub">Ignorados pela regra de validade, por motivo</h4>
          @if (d.ignorados; as ignorados) {
            <ul class="diag-itens" aria-label="Lançamentos ignorados, por motivo">
              @for (i of ignorados; track i.motivo) {
                <li><span>{{ i.texto }}</span><b>{{ i.quantos }}</b></li>
              }
            </ul>
          } @else {
            <p class="muted">A resposta não trouxe o diagnóstico (só o Administrador o recebe).</p>
          }

          <h4 class="diag-sub">Origem da data do "Isento de Taxa"</h4>
          @if (d.isencoes.length) {
            <ul class="diag-itens" aria-label="Origem da data de cada isenção">
              @for (i of d.isencoes; track i.ordem) {
                <li>
                  <span>{{ i.ordem }}ª isenção: {{ i.texto }}</span>
                  @if (i.naoProcessado) {
                    <span class="tag gray">não processado</span>
                  }
                </li>
              }
            </ul>
          } @else {
            <p class="muted">Este requerimento não tem isenção.</p>
          }
        </div>
      }
    </section>
  `,
  styles: `
    :host { display: block; margin-top: 24px; }
    .diag-form { display: flex; flex-wrap: wrap; align-items: flex-end; gap: 10px 14px; margin-bottom: 12px; }
    .diag-form .field { min-width: 220px; flex: 1 1 220px; max-width: 320px; }
    .btn:focus-visible, input:focus-visible { outline: 2px solid var(--forest); outline-offset: 2px; }
    .diag-resultado { margin-top: 8px; padding: 12px 14px; border: 1px solid var(--line); border-radius: 10px; }
    .diag-titulo { margin: 0 0 8px; }
    .diag-lista { display: flex; flex-wrap: wrap; gap: 6px 28px; margin: 0 0 8px; }
    .diag-lista dt { font-size: 11px; color: var(--muted); }
    .diag-lista dd { margin: 0; font-size: 16px; font-weight: 700; }
    .diag-sub { margin: 12px 0 6px; font-size: 13px; }
    .diag-itens { list-style: none; margin: 0; padding: 0; display: grid; gap: 6px; font-size: 13px; }
    .diag-itens li { display: flex; flex-wrap: wrap; align-items: baseline; justify-content: space-between; gap: 4px 12px; overflow-wrap: anywhere; }
  `,
})
export class DiagnosticoFinanceiroComponent {
  private readonly api = inject(DetalheApiService);

  protected readonly id = signal('');
  protected readonly carregando = signal(false);
  protected readonly aviso = signal('');
  protected readonly diagnostico = signal<DiagnosticoDoFinanceiro | null>(null);

  private assinatura: Subscription | null = null;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.assinatura?.unsubscribe());
  }

  protected digitar(evento: Event): void {
    this.id.set((evento.target as HTMLInputElement).value.replace(/\D/g, ''));
  }

  protected consultar(): void {
    const texto = this.id().trim();
    if (!/^[1-9]\d{0,11}$/.test(texto)) {
      this.diagnostico.set(null);
      this.aviso.set('Informe o identificador do requerimento (só números).');
      return;
    }
    this.assinatura?.unsubscribe();
    this.aviso.set('');
    this.diagnostico.set(null);
    this.carregando.set(true);
    this.assinatura = this.api.financeiro(Number(texto)).subscribe({
      next: (f) => {
        this.carregando.set(false);
        const d = diagnosticoDe(f);
        if (d) {
          this.diagnostico.set(d);
        } else {
          this.aviso.set('Não foi possível consultar o financeiro agora. Tente de novo mais tarde.');
        }
      },
      error: (e: unknown) => {
        this.carregando.set(false);
        this.aviso.set(
          e instanceof HttpErrorResponse ? mensagemDoErro(e) : 'Não foi possível consultar o financeiro agora. Tente de novo mais tarde.',
        );
      },
    });
  }
}
