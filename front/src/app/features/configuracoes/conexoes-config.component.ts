import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { Subscription } from 'rxjs';

import { mensagemDoErro } from '../../core/mensagens-erro';
import { IconeComponent } from '../../shared/icone.component';
import { ConfiguracoesApiService } from './configuracoes-api.service';
import { Conexao, ROTULO_DA_CLASSIFICACAO, TOM_DA_CLASSIFICACAO, TesteDeConexao } from './configuracoes.models';
import { ParametrosAbaComponent } from './parametros-aba.component';

/** Orientação quando o token da API de arquivos está vencido (sem citar valor algum). */
export const MENSAGEM_DE_TOKEN_VENCIDO =
  'O token da API de arquivos está vencido. Capture um novo token, atualize o arquivo do token e clique em Tentar novamente.';

interface ResultadoDaLinha {
  testando: boolean;
  teste: TesteDeConexao | null;
  /** Falha do próprio pedido de teste (sem resposta do servidor), por exemplo o 409 de teste em andamento. */
  erro: string;
}

/**
 * Aba "Conexões" (RF27, docs/12): estado e "Testar conexão" do banco do SIGA, da API de arquivos (com as horas até
 * expirar o token), do SCORP e das taxas. O servidor responde só estado, tempo e mensagem: **nunca** URL, host ou
 * credencial, e a tela também não oferece campo para editá-las. Reúne o tempo limite de download, o único parâmetro
 * editável desta seção.
 */
@Component({
  selector: 'app-conexoes-config',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconeComponent, ParametrosAbaComponent],
  templateUrl: './conexoes-config.component.html',
})
export class ConexoesConfigComponent implements OnInit {
  private readonly api = inject(ConfiguracoesApiService);

  protected readonly carregando = signal(true);
  protected readonly erro = signal('');
  protected readonly conexoes = signal<Conexao[]>([]);
  protected readonly linhas = signal<Readonly<Record<string, ResultadoDaLinha>>>({});
  protected readonly mensagemDeToken = MENSAGEM_DE_TOKEN_VENCIDO;
  /** Tempo limite de cada chamada do teste (CONEXAO_TESTE_TIMEOUT_SEGUNDOS), mostrado antes de testar. */
  protected readonly tempoLimite = signal<number | null>(null);
  protected readonly rotuloDaClassificacao = ROTULO_DA_CLASSIFICACAO;
  protected readonly tomDaClassificacao = TOM_DA_CLASSIFICACAO;

  private carga: Subscription | null = null;
  private readonly testes = new Map<string, Subscription>();

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      this.carga?.unsubscribe();
      this.testes.forEach((s) => s.unsubscribe());
      this.testes.clear();
    });
  }

  ngOnInit(): void {
    this.carregar();
  }

  protected carregar(): void {
    this.carga?.unsubscribe();
    this.carregando.set(true);
    this.erro.set('');
    this.carga = this.api.conexoes().subscribe({
      next: (resposta) => {
        this.conexoes.set(resposta.conexoes);
        this.tempoLimite.set(resposta.tempoLimiteDoTesteSegundos ?? null);
        this.carregando.set(false);
      },
      error: (erro: unknown) => {
        this.carregando.set(false);
        this.erro.set(erro instanceof HttpErrorResponse ? mensagemDoErro(erro) : 'Não foi possível carregar as conexões.');
      },
    });
  }

  protected linha(c: Conexao): ResultadoDaLinha {
    return this.linhas()[c.nome] ?? { testando: false, teste: null, erro: '' };
  }

  /** Horas até expirar: a do último teste, se houver; senão a da lista. */
  protected horas(c: Conexao): number | null {
    const teste = this.linha(c).teste;
    return teste ? teste.horasAteExpirar : c.horasAteExpirar;
  }

  protected vencido(c: Conexao): boolean {
    const teste = this.linha(c).teste;
    return teste ? teste.tokenVencido : c.tokenVencido;
  }

  /** O botão vira "Tentar novamente" depois de um teste que falhou. */
  protected rotuloDoBotao(c: Conexao): string {
    const l = this.linha(c);
    if (l.testando) {
      return 'Testando…';
    }
    return l.erro || (l.teste && l.teste.classificacao !== 'OK') || this.vencido(c) ? 'Tentar novamente' : 'Testar conexão';
  }

  protected testar(c: Conexao): void {
    if (this.linha(c).testando || c.estado === 'DESATIVADA') {
      return;
    }
    this.testes.get(c.nome)?.unsubscribe();
    this.definir(c.nome, { testando: true, teste: this.linha(c).teste, erro: '' });
    this.testes.set(
      c.nome,
      this.api.testarConexao(c.nome).subscribe({
        next: (teste) => this.definir(c.nome, { testando: false, teste, erro: '' }),
        error: (erro: unknown) =>
          this.definir(c.nome, {
            testando: false,
            teste: null,
            erro: erro instanceof HttpErrorResponse ? mensagemDoErro(erro) : 'Não foi possível testar a conexão.',
          }),
      }),
    );
  }

  private definir(nome: string, linha: ResultadoDaLinha): void {
    this.linhas.update((atual) => ({ ...atual, [nome]: linha }));
  }

  protected rotuloDoEstado(c: Conexao): string {
    return { CONFIGURADA: 'Configurada', NAO_CONFIGURADA: 'Não configurada', DESATIVADA: 'Desativada' }[c.estado];
  }

  protected tomDoEstado(c: Conexao): string {
    return { CONFIGURADA: 'ok', NAO_CONFIGURADA: 'warn', DESATIVADA: 'gray' }[c.estado];
  }
}
