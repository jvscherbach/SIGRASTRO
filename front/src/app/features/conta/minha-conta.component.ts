import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, computed, inject, signal } from '@angular/core';
import { Subscription } from 'rxjs';

import { AuthService } from '../../core/auth.service';
import { mensagemDoBloqueio, mensagemDoErro } from '../../core/mensagens-erro';
import { ProblemDetail } from '../../core/usuario.model';
import { FolhaSobrepostaComponent } from '../../shared/folha-sobreposta.component';
import { IconeComponent } from '../../shared/icone.component';
import { QuadroTopoComponent } from '../../shared/quadro-topo.component';
import { ContaApiService } from './conta-api.service';

type Campo = 'senhaAtual' | 'novaSenha' | 'confirmacao';

/** Política de senha mostrada na tela (a mesma do servidor, que repete a conferência). */
export const POLITICA_DE_SENHA = [
  'De 12 a 200 caracteres.',
  'Não pode ser só espaços.',
  'Não pode ser igual ao seu login nem conter o login.',
  'Deve ser diferente da senha atual.',
];

/**
 * Minha conta (L1): todo perfil troca a própria senha. Os valores digitados ficam só nos campos do formulário e no corpo
 * do POST: depois do envio, com sucesso ou não, os campos de senha são limpos. As outras sessões do usuário são
 * encerradas pelo servidor; esta continua aberta.
 */
@Component({
  selector: 'app-minha-conta',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [QuadroTopoComponent, FolhaSobrepostaComponent, IconeComponent],
  templateUrl: './minha-conta.component.html',
})
export class MinhaContaComponent {
  private readonly api = inject(ContaApiService);
  private readonly auth = inject(AuthService);
  private readonly anfitriao = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;

  protected readonly politica = POLITICA_DE_SENHA;
  protected readonly usuario = this.auth.usuario;

  protected readonly senhaAtual = signal('');
  protected readonly novaSenha = signal('');
  protected readonly confirmacao = signal('');
  protected readonly tentou = signal(false);
  protected readonly enviando = signal(false);
  protected readonly sucesso = signal(false);
  protected readonly erroGeral = signal('');
  protected readonly erroDoServidor = signal<Partial<Record<Campo, string>>>({});

  protected readonly erros = computed<Partial<Record<Campo, string>>>(() => {
    const resultado: Partial<Record<Campo, string>> = {};
    if (!this.senhaAtual()) {
      resultado.senhaAtual = 'Informe a senha atual.';
    }
    const nova = this.novaSenha();
    if (!nova) {
      resultado.novaSenha = 'Informe a nova senha.';
    } else if (nova.length < 12) {
      resultado.novaSenha = 'A nova senha precisa ter pelo menos 12 caracteres.';
    } else if (nova.length > 200) {
      resultado.novaSenha = 'A nova senha pode ter no máximo 200 caracteres.';
    } else if (!nova.trim()) {
      resultado.novaSenha = 'A nova senha não pode ser só espaços.';
    }
    if (!this.confirmacao()) {
      resultado.confirmacao = 'Confirme a nova senha.';
    } else if (this.confirmacao() !== nova) {
      resultado.confirmacao = 'A confirmação não confere com a nova senha.';
    }
    return resultado;
  });

  private envio: Subscription | null = null;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.envio?.unsubscribe());
  }

  protected mensagemDe(campo: Campo): string {
    const local = this.tentou() ? this.erros()[campo] : undefined;
    return local ?? this.erroDoServidor()[campo] ?? '';
  }

  protected digitar(campo: Campo, evento: Event): void {
    const valor = (evento.target as HTMLInputElement).value;
    this.sucesso.set(false);
    this.erroDoServidor.update((atual) => ({ ...atual, [campo]: '' }));
    this.erroGeral.set('');
    if (campo === 'senhaAtual') {
      this.senhaAtual.set(valor);
    } else if (campo === 'novaSenha') {
      this.novaSenha.set(valor);
    } else {
      this.confirmacao.set(valor);
    }
  }

  protected trocar(): void {
    this.tentou.set(true);
    this.erroGeral.set('');
    this.sucesso.set(false);
    if (this.enviando()) {
      return;
    }
    const primeiroErro = (['senhaAtual', 'novaSenha', 'confirmacao'] as Campo[]).find((c) => this.erros()[c]);
    if (primeiroErro) {
      this.focar(primeiroErro);
      return;
    }
    this.enviando.set(true);
    this.envio = this.api
      .trocarSenha({ senhaAtual: this.senhaAtual(), novaSenha: this.novaSenha(), confirmacao: this.confirmacao() })
      .subscribe({
        next: () => {
          this.enviando.set(false);
          this.limpar();
          this.tentou.set(false);
          this.sucesso.set(true);
        },
        error: (erro: unknown) => {
          this.enviando.set(false);
          this.limpar();
          // os campos foram apagados: só a mensagem do servidor fica, até o próximo envio
          this.tentou.set(false);
          this.tratarErro(erro);
        },
      });
  }

  /** Apaga os três valores da memória da tela e dos campos. */
  private limpar(): void {
    this.senhaAtual.set('');
    this.novaSenha.set('');
    this.confirmacao.set('');
    this.anfitriao.querySelectorAll<HTMLInputElement>('input[type="password"]').forEach((campo) => (campo.value = ''));
  }

  private tratarErro(erro: unknown): void {
    if (!(erro instanceof HttpErrorResponse)) {
      this.erroGeral.set('Não foi possível trocar a senha. Tente novamente.');
      return;
    }
    const problema = erro.error as (ProblemDetail & { segundosRestantes?: number }) | null;
    if (erro.status === 429) {
      this.erroGeral.set(mensagemDoBloqueio(problema?.segundosRestantes));
    } else if (erro.status === 400 && problema?.campos?.length) {
      const porCampo: Partial<Record<Campo, string>> = {};
      for (const c of problema.campos) {
        if (c.campo === 'senhaAtual' || c.campo === 'novaSenha' || c.campo === 'confirmacao') {
          porCampo[c.campo] = c.mensagem;
        }
      }
      this.erroDoServidor.set(porCampo);
      const primeiro = (['senhaAtual', 'novaSenha', 'confirmacao'] as Campo[]).find((c) => porCampo[c]);
      if (primeiro) {
        this.focar(primeiro);
      } else {
        this.erroGeral.set('Verifique os dados informados e tente novamente.');
      }
    } else {
      this.erroGeral.set(mensagemDoErro(erro));
    }
  }

  private focar(campo: Campo): void {
    setTimeout(() => this.anfitriao.querySelector<HTMLInputElement>(`#mc-${campo}`)?.focus());
  }
}
