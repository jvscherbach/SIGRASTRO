import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';

import { IconeComponent } from '../../shared/icone.component';
import { Objeto } from './detalhe.models';

const LIMITE_INICIAL = 5;
const SEM_FINALIDADE = 'Finalidade não informada';

interface LinhaDeObjetivo {
  tipo: string | null;
  texto: string;
}

interface GrupoDeFinalidade {
  finalidade: string;
  atividades: string[];
}

interface AtividadeExibida {
  principal: string;
  grupo: string | null;
}

/**
 * Objeto do requerimento (P01), em cartões que só existem quando há dados. Objetivos e pares finalidade/atividade são
 * listas independentes; "Tipo | Objetivo" é a mesma lista dos objetivos (o SIGA a mostra duplicada), então os dois
 * viram um cartão só. Os textos vêm do SIGA e aparecem como estão.
 */
@Component({
  selector: 'app-objeto-do-requerimento',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconeComponent],
  template: `
    @if (vazio()) {
      <div class="note" role="status"><app-icone nome="info" /><span>Sem objeto cadastrado para este requerimento</span></div>
    } @else {
      <div class="objeto-grid">
        @if (objetivos().length) {
          <section class="objeto-card" aria-label="Objetivos">
            <header>
              <span class="tile-icon"><app-icone nome="drop" /></span>
              <h3>Objetivos</h3>
              @if (objetivos().length > 1) {
                <span class="contador" [attr.aria-label]="objetivos().length + ' objetivos'">{{ objetivos().length }}</span>
              }
            </header>
            <ul class="objeto-linhas">
              @for (linha of objetivosVisiveis(); track $index) {
                <li>
                  @if (linha.tipo) {
                    <span class="obj-tipo">{{ linha.tipo }}</span>
                  }
                  <span class="obj-texto">{{ linha.texto }}</span>
                </li>
              }
            </ul>
            @if (objetivos().length > limite) {
              <button type="button" class="link-btn mostrar-todos" (click)="alternar()">
                {{ expandido() ? 'Mostrar menos' : 'Mostrar todos (' + objetivos().length + ')' }}
              </button>
            }
          </section>
        }

        @if (grupos().length) {
          <section class="objeto-card" aria-label="Finalidade e atividade">
            <header>
              <span class="tile-icon"><app-icone nome="bars" /></span>
              <h3>Finalidade e atividade</h3>
            </header>
            @for (grupo of grupos(); track grupo.finalidade) {
              <div class="obj-grupo">
                <p class="obj-finalidade">{{ grupo.finalidade }}</p>
                @if (grupo.atividades.length) {
                  <ul class="obj-atividades">
                    @for (atividade of grupo.atividades; track atividade) {
                      <li>{{ atividade }}</li>
                    }
                  </ul>
                }
              </div>
            }
          </section>
        }

        @if (atividade(); as a) {
          <section class="objeto-card" aria-label="Atividade">
            <header>
              <span class="tile-icon"><app-icone nome="building" /></span>
              <h3>Atividade</h3>
            </header>
            <p class="obj-atividade-principal">{{ a.principal }}</p>
            @if (a.grupo) {
              <p class="obj-grupo-legenda">Grupo: {{ a.grupo }}</p>
            }
          </section>
        }
      </div>
    }
  `,
})
export class ObjetoDoRequerimentoComponent {
  readonly objeto = input.required<Objeto>();

  protected readonly limite = LIMITE_INICIAL;
  protected readonly expandido = signal(false);

  /** Uma linha por objetivo; com o tipo (Superficial, Subterrânea, Barramento) quando a outorga o informa. */
  protected readonly objetivos = computed<LinhaDeObjetivo[]>(() => {
    const objeto = this.objeto();
    const comTipo = objeto.tiposObjetivo
      .filter((t) => preenchido(t.objetivo))
      .map((t) => ({ tipo: preenchido(t.tipoDescricao) ? t.tipoDescricao!.trim() : null, texto: t.objetivo!.trim() }));
    if (comTipo.length) {
      return comTipo;
    }
    return objeto.objetivos.filter(preenchido).map((texto) => ({ tipo: null, texto: texto.trim() }));
  });

  protected readonly objetivosVisiveis = computed(() =>
    this.expandido() ? this.objetivos() : this.objetivos().slice(0, LIMITE_INICIAL),
  );

  /** Agrupado por finalidade: a finalidade aparece uma vez, com as atividades dela (sem repetir). */
  protected readonly grupos = computed<GrupoDeFinalidade[]>(() => {
    const porFinalidade = new Map<string, string[]>();
    for (const par of this.objeto().finalidadesAtividades) {
      const finalidade = preenchido(par.finalidade) ? par.finalidade!.trim() : SEM_FINALIDADE;
      const atividades = porFinalidade.get(finalidade) ?? [];
      if (preenchido(par.atividade) && !atividades.includes(par.atividade!.trim())) {
        atividades.push(par.atividade!.trim());
      }
      porFinalidade.set(finalidade, atividades);
    }
    return Array.from(porFinalidade, ([finalidade, atividades]) => ({ finalidade, atividades }));
  });

  /** Licenciamento: a atividade é o texto principal; o grupo só aparece como legenda se for diferente dela. */
  protected readonly atividade = computed<AtividadeExibida | null>(() => {
    const a = this.objeto().atividadeLicenciamento;
    if (!a) {
      return null;
    }
    const atividade = preenchido(a.atividade) ? a.atividade!.trim() : null;
    const grupo = preenchido(a.grupo) ? a.grupo!.trim() : null;
    if (!atividade && !grupo) {
      return null;
    }
    if (!atividade) {
      return { principal: grupo!, grupo: null };
    }
    return { principal: atividade, grupo: grupo && comparavel(grupo) !== comparavel(atividade) ? grupo : null };
  });

  protected readonly vazio = computed(
    () => !this.objetivos().length && !this.grupos().length && this.atividade() === null,
  );

  protected alternar(): void {
    this.expandido.update((atual) => !atual);
  }
}

function preenchido(texto: string | null | undefined): boolean {
  return !!texto && texto.trim().length > 0;
}

/** Sem acento, sem diferenciar maiúsculas e sem espaços repetidos. */
function comparavel(texto: string): string {
  return texto
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .replace(/\s+/g, ' ')
    .trim()
    .toLowerCase();
}
