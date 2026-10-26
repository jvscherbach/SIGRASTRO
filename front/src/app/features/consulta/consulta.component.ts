import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

import { AuthService } from '../../core/auth.service';
import { mensagemDoErro } from '../../core/mensagens-erro';
import { PageResponse, Interessado } from '../../shared/api-tipos';
import { formatarCpfCnpj, formatarData, mascararCpfCnpj, mascararNumero, somenteDigitos } from '../../shared/formato';
import { FolhaSobrepostaComponent } from '../../shared/folha-sobreposta.component';
import { IconeComponent } from '../../shared/icone.component';
import { QuadroTopoComponent } from '../../shared/quadro-topo.component';
import { RecentesService } from '../../shared/recentes.service';
import { SituacaoTagComponent } from '../../shared/situacao-tag.component';
import { ConsultaApiService } from './consulta-api.service';
import { CriterioBusca, ResultadoBusca, TipoBusca } from './consulta.models';

const FORMATO_NUMERO = /^\d{1,6}\/\d{4}$/;
const NOME_MINIMO = 3;

@Component({
  selector: 'app-consulta',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [QuadroTopoComponent, FolhaSobrepostaComponent, IconeComponent, SituacaoTagComponent],
  templateUrl: './consulta.component.html',
})
export class ConsultaComponent {
  private readonly api = inject(ConsultaApiService);
  private readonly router = inject(Router);
  private readonly recentesService = inject(RecentesService);
  private readonly auth = inject(AuthService);

  protected readonly tipo = signal<TipoBusca>('REQUERIMENTO');
  protected readonly numero = signal('');
  protected readonly cpfCnpj = signal('');
  protected readonly nome = signal('');
  protected readonly erro = signal('');
  protected readonly carregando = signal(false);
  protected readonly resultado = signal<PageResponse<ResultadoBusca> | null>(null);
  protected readonly recentes = signal(this.recentesService.listarParaExibicao());
  protected readonly processoRepetido = signal(false);

  private criterioAtual: CriterioBusca | null = null;

  protected readonly restrito = computed(() => this.auth.usuario()?.permissoes.restritoAoInteressado ?? false);
  protected readonly rotuloDoTipo = computed(() => (this.tipo() === 'PROCESSO' ? 'processo' : 'requerimento'));
  protected readonly exemploDoNumero = computed(() => (this.tipo() === 'PROCESSO' ? '2825/2025' : '3331/2025'));
  protected readonly paginaExibida = computed(() => (this.resultado()?.pagina ?? 0) + 1);

  protected escolherTipo(tipo: TipoBusca): void {
    this.tipo.set(tipo);
  }

  protected digitarNumero(evento: Event): void {
    const campo = evento.target as HTMLInputElement;
    campo.value = mascararNumero(campo.value);
    this.numero.set(campo.value);
  }

  protected digitarCpfCnpj(evento: Event): void {
    const campo = evento.target as HTMLInputElement;
    campo.value = mascararCpfCnpj(campo.value);
    this.cpfCnpj.set(campo.value);
  }

  protected digitarNome(evento: Event): void {
    this.nome.set((evento.target as HTMLInputElement).value);
  }

  protected consultar(evento?: Event): void {
    evento?.preventDefault();
    const criterio = this.montarCriterio();
    if (typeof criterio === 'string') {
      this.erro.set(criterio);
      this.resultado.set(null);
      return;
    }
    this.executar(criterio);
  }

  protected limpar(): void {
    this.numero.set('');
    this.cpfCnpj.set('');
    this.nome.set('');
    this.erro.set('');
    this.resultado.set(null);
    this.criterioAtual = null;
    this.processoRepetido.set(false);
  }

  protected irParaPagina(pagina: number): void {
    if (this.criterioAtual) {
      this.executar({ ...this.criterioAtual, pagina });
    }
  }

  protected abrir(id: number): void {
    void this.router.navigate(['/consulta/requerimentos', id]);
  }

  protected fecharResultados(): void {
    this.resultado.set(null);
  }

  protected formatarCpfCnpj = formatarCpfCnpj;
  protected formatarData = formatarData;

  protected nomeDe(interessado: Interessado | null): string {
    return interessado?.nome || '—';
  }

  /** Devolve os critérios normalizados ou a mensagem do primeiro problema encontrado. */
  private montarCriterio(): CriterioBusca | string {
    const numero = this.numero().trim();
    const digitos = somenteDigitos(this.cpfCnpj());
    const nome = this.nome().trim();

    if (!numero && !digitos && !nome) {
      return 'Informe ao menos um critério de pesquisa.';
    }
    if (numero && !FORMATO_NUMERO.test(numero)) {
      return 'Informe o número no formato N/AAAA.';
    }
    if (digitos && digitos.length !== 11 && digitos.length !== 14) {
      return 'Informe um CPF (11 dígitos) ou CNPJ (14 dígitos).';
    }
    if (nome && nome.length < NOME_MINIMO) {
      return `Informe ao menos ${NOME_MINIMO} caracteres do nome.`;
    }
    return {
      tipo: numero ? this.tipo() : undefined,
      numero: numero || undefined,
      cpfCnpj: digitos || undefined,
      nome: nome || undefined,
      pagina: 0,
    };
  }

  private executar(criterio: CriterioBusca): void {
    this.erro.set('');
    this.carregando.set(true);
    this.api.buscar(criterio).subscribe({
      next: (pagina) => {
        this.carregando.set(false);
        this.criterioAtual = criterio;
        const soNumero = !!criterio.numero && !criterio.cpfCnpj && !criterio.nome;
        if (soNumero && pagina.totalElementos === 1 && pagina.conteudo.length === 1) {
          this.abrir(pagina.conteudo[0].id);
          return;
        }
        // Número de processo não é único no SIGA (1:N): com mais de um, o usuário escolhe na lista.
        this.processoRepetido.set(soNumero && criterio.tipo === 'PROCESSO' && pagina.totalElementos > 1);
        this.resultado.set(pagina);
      },
      error: (erro: unknown) => {
        this.carregando.set(false);
        this.resultado.set(null);
        this.erro.set(mensagemDaBusca(erro, criterio));
      },
    });
  }
}

/** Mensagem inline da busca; usa o errorCode, nunca o detail técnico cru. */
export function mensagemDaBusca(erro: unknown, criterio: CriterioBusca): string {
  if (!(erro instanceof HttpErrorResponse)) {
    return 'Não foi possível consultar. Tente novamente.';
  }
  const alvo = criterio.numero ? `${criterio.tipo === 'PROCESSO' ? 'Processo' : 'Requerimento'} ${criterio.numero}` : '';
  const problema = erro.error as { errorCode?: string; campos?: { mensagem: string }[] } | null;
  switch (problema?.errorCode) {
    case 'NAO_INTERESSADO':
      return alvo ? `${alvo} não tem você como interessado.` : mensagemDoErro(erro);
    case 'NAO_ENCONTRADO':
      return alvo ? `${alvo} não encontrado.` : 'Nenhum resultado encontrado.';
    case 'PARAMETRO_INVALIDO':
      return problema.campos?.[0]?.mensagem ?? mensagemDoErro(erro);
    default:
      return mensagemDoErro(erro);
  }
}
