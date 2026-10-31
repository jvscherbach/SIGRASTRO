import { HttpErrorResponse } from '@angular/common/http';
import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { Subscription } from 'rxjs';

import { mensagemDoErro } from '../../core/mensagens-erro';
import { ConfiguracoesApiService } from './configuracoes-api.service';
import {
  ClasseDeAplicacao,
  GrupoDeParametro,
  Parametro,
  ROTULO_DA_OPCAO,
  ValorDeParametro,
} from './configuracoes.models';
import { camposInvalidos, mensagemDaConfiguracao, recusaPorCampos } from './mensagens-config';

/** O que mudou num salvamento, para o resumo mostrado depois. */
export interface MudancaSalva {
  nome: string;
  rotulo: string;
  de: string;
  para: string;
  classe: ClasseDeAplicacao;
}

export interface ResumoDoSalvamento {
  mudancas: MudancaSalva[];
  /** Os que só valem de fato depois de reiniciar (e então pela variável de ambiente). */
  exigemReinicio: MudancaSalva[];
}

/** Texto de um valor para a pessoa: ligado/desligado, opção com rótulo ou o número como veio. */
export function textoDoValor(p: Pick<Parametro, 'tipo'>, valor: string): string {
  if (p.tipo === 'BOOLEANO') {
    return valor === 'true' ? 'ligado' : 'desligado';
  }
  return ROTULO_DA_OPCAO[valor] ?? valor;
}

/**
 * Estado dos parâmetros da tela de Configurações (RF26 a RF28): a lista do servidor, as alterações pendentes (editar não
 * grava) e o salvamento em um único `PUT` com todas elas. Um serviço por tela (provido no componente), compartilhado
 * pelas abas, para que as pendências sobrevivam à troca de aba e a barra fixa conte todas.
 */
@Injectable()
export class ParametrosStore {
  private readonly api = inject(ConfiguracoesApiService);

  readonly carregando = signal(true);
  readonly erro = signal('');
  readonly parametros = signal<Parametro[]>([]);
  /** nome → valor (texto) ainda não salvo. */
  readonly pendencias = signal<Readonly<Record<string, string>>>({});
  readonly salvando = signal(false);
  /** Recusa do servidor por parâmetro (faixa ou valor); mantém as pendências. */
  readonly errosPorNome = signal<Readonly<Record<string, string>>>({});
  readonly erroGeral = signal('');
  readonly resumo = signal<ResumoDoSalvamento | null>(null);
  /** Parâmetro cuja restauração do padrão está em andamento. */
  readonly restaurando = signal<string | null>(null);

  readonly quantidadePendente = computed(() => Object.keys(this.pendencias()).length);
  readonly temPendencia = computed(() => this.quantidadePendente() > 0);

  private carga: Subscription | null = null;
  private gravacao: Subscription | null = null;

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      this.carga?.unsubscribe();
      this.gravacao?.unsubscribe();
    });
  }

  doGrupo(grupo: GrupoDeParametro): Parametro[] {
    return this.parametros().filter((p) => p.grupo === grupo);
  }

  carregar(): void {
    this.carga?.unsubscribe();
    this.carregando.set(true);
    this.erro.set('');
    this.carga = this.api.parametros().subscribe({
      next: (resposta) => {
        this.parametros.set(resposta.parametros);
        this.carregando.set(false);
      },
      error: (erro: unknown) => {
        this.carregando.set(false);
        this.erro.set(erro instanceof HttpErrorResponse ? mensagemDoErro(erro) : 'Não foi possível carregar os parâmetros.');
      },
    });
  }

  /** Valor mostrado no campo: o pendente, se houver; senão o vigente. */
  valorDe(p: Parametro): string {
    return this.pendencias()[p.nome] ?? p.valor;
  }

  pendente(nome: string): boolean {
    return nome in this.pendencias();
  }

  /** Editar não grava. Voltar ao valor vigente tira a pendência. */
  editar(p: Parametro, valor: string): void {
    this.resumo.set(null);
    this.errosPorNome.update(({ [p.nome]: _, ...resto }) => resto);
    this.pendencias.update((atual) => {
      const { [p.nome]: _, ...resto } = atual;
      return valor === p.valor ? resto : { ...resto, [p.nome]: valor };
    });
  }

  descartar(): void {
    this.pendencias.set({});
    this.errosPorNome.set({});
    this.erroGeral.set('');
  }

  salvar(): void {
    if (!this.temPendencia() || this.salvando()) {
      return;
    }
    const antes = this.parametros();
    const pendencias = this.pendencias();
    const alteracoes: Record<string, ValorDeParametro> = {};
    for (const p of antes) {
      if (p.nome in pendencias) {
        alteracoes[p.nome] = this.valorTipado(p, pendencias[p.nome]);
      }
    }
    this.salvando.set(true);
    this.erroGeral.set('');
    this.errosPorNome.set({});
    this.resumo.set(null);
    this.gravacao = this.api.salvarParametros(alteracoes).subscribe({
      next: (resposta) => {
        this.salvando.set(false);
        this.parametros.set(resposta.parametros);
        this.pendencias.set({});
        this.resumo.set(this.montarResumo(antes, resposta.parametros, pendencias));
      },
      error: (erro: unknown) => {
        this.salvando.set(false);
        // nada foi aplicado: as pendências ficam para o usuário corrigir
        if (recusaPorCampos(erro)) {
          this.errosPorNome.set(camposInvalidos(erro));
        } else {
          this.erroGeral.set(mensagemDaConfiguracao(erro));
        }
      },
    });
  }

  /** Volta o parâmetro ao padrão do ambiente (DELETE). A pendência dele, se houver, sai. */
  restaurar(p: Parametro): void {
    if (this.restaurando() !== null) {
      return;
    }
    this.restaurando.set(p.nome);
    this.erroGeral.set('');
    this.gravacao = this.api.restaurarParametro(p.nome).subscribe({
      next: (resposta) => {
        this.restaurando.set(null);
        this.parametros.set(resposta.parametros);
        this.pendencias.update(({ [p.nome]: _, ...resto }) => resto);
        this.errosPorNome.update(({ [p.nome]: _, ...resto }) => resto);
      },
      error: (erro: unknown) => {
        this.restaurando.set(null);
        this.erroGeral.set(mensagemDaConfiguracao(erro));
      },
    });
  }

  private valorTipado(p: Parametro, texto: string): ValorDeParametro {
    if (p.tipo === 'BOOLEANO') {
      return texto === 'true';
    }
    if (p.tipo === 'INTEIRO') {
      const numero = Number(texto);
      // texto que não é número segue como veio: o servidor recusa e marca o campo
      return texto.trim() !== '' && Number.isFinite(numero) ? numero : texto;
    }
    return texto;
  }

  private montarResumo(antes: Parametro[], depois: Parametro[], pendencias: Readonly<Record<string, string>>): ResumoDoSalvamento {
    const mudancas: MudancaSalva[] = [];
    for (const anterior of antes) {
      if (!(anterior.nome in pendencias)) {
        continue;
      }
      const atual = depois.find((p) => p.nome === anterior.nome) ?? anterior;
      // salvar o próprio padrão ou o mesmo valor não é mudança
      if (anterior.valor === pendencias[anterior.nome]) {
        continue;
      }
      mudancas.push({
        nome: anterior.nome,
        rotulo: anterior.rotulo,
        de: textoDoValor(anterior, anterior.valor),
        para: textoDoValor(atual, pendencias[anterior.nome]),
        classe: anterior.classeDeAplicacao,
      });
    }
    return { mudancas, exigemReinicio: mudancas.filter((m) => m.classe === 'EXIGE_REINICIO') };
  }
}
