import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { IconeComponent } from '../../shared/icone.component';
import { ROTULO_DA_CLASSE } from './configuracoes.models';
import { ParametrosStore } from './parametros-store.service';

/**
 * Barra fixa das alterações pendentes (RF28): aparece com pelo menos uma, mostra quantas são e oferece "Salvar
 * alterações" (um único `PUT` com todas) e "Descartar". Depois de salvar, mostra o resumo do que mudou e o que exige
 * reinício, até o usuário dispensar ou editar de novo.
 */
@Component({
  selector: 'app-barra-pendencias',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconeComponent],
  template: `
    @if (store.resumo(); as r) {
      <div class="cfg-resumo note ok" role="status">
        <app-icone nome="check" />
        <div>
          @if (r.mudancas.length) {
            <b>{{ r.mudancas.length === 1 ? '1 alteração salva' : r.mudancas.length + ' alterações salvas' }}.</b>
            <ul>
              @for (m of r.mudancas; track m.nome) {
                <li>{{ m.rotulo }}: {{ m.de }} → {{ m.para }} <span class="tag gray">{{ rotulos[m.classe] }}</span></li>
              }
            </ul>
          } @else {
            <b>Nada mudou:</b> os valores já eram os vigentes.
          }
          @if (r.exigemReinicio.length) {
            <p class="cfg-aviso-reinicio">
              <b>Exigem reinício:</b> {{ nomes(r.exigemReinicio) }}. O valor em vigor não mudou. Como as alterações desta tela
              somem ao reiniciar o serviço, para valer de fato altere a variável de ambiente do servidor.
            </p>
          }
        </div>
        <button type="button" class="icon-btn" aria-label="Dispensar resumo" (click)="store.resumo.set(null)"><app-icone nome="close" /></button>
      </div>
    }
    @if (store.erroGeral()) {
      <div class="alert cfg-erro-salvar" role="alert"><app-icone nome="alert" /><span>{{ store.erroGeral() }}</span></div>
    }
    @if (store.temPendencia()) {
      <div class="cfg-barra" role="region" aria-label="Alterações pendentes">
        <span class="cfg-barra-contagem">
          <b>{{ store.quantidadePendente() }}</b> {{ store.quantidadePendente() === 1 ? 'alteração pendente' : 'alterações pendentes' }}
          @if (Object.keys(store.errosPorNome()).length) {
            <span class="tag bad">Há campos com erro</span>
          }
        </span>
        <span class="row">
          <button type="button" class="btn quiet cfg-descartar" [disabled]="store.salvando()" (click)="store.descartar()">Descartar</button>
          <button type="button" class="btn primary cfg-salvar" [disabled]="store.salvando()" (click)="store.salvar()">
            {{ store.salvando() ? 'Salvando…' : 'Salvar alterações' }}
          </button>
        </span>
      </div>
    }
  `,
})
export class BarraPendenciasComponent {
  protected readonly store = inject(ParametrosStore);
  protected readonly rotulos = ROTULO_DA_CLASSE;
  protected readonly Object = Object;

  protected nomes(lista: { rotulo: string }[]): string {
    return lista.map((m) => m.rotulo).join(', ');
  }
}
