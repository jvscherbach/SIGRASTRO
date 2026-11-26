import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { Subscription } from 'rxjs';

import { cpfCnpjValido } from '../../shared/documento-fiscal';
import { mascararCpfCnpj, somenteDigitos } from '../../shared/formato';
import { FocoPresoDirective } from '../../shared/foco-preso.directive';
import { IconeComponent } from '../../shared/icone.component';
import { ConfiguracoesApiService } from './configuracoes-api.service';
import { LOGIN_VALIDO, PERFIS_ASSINAVEIS, UsuarioConfigurado } from './configuracoes.models';
import { camposInvalidos, mensagemDaConfiguracao, recusaPorCampos } from './mensagens-config';

type Campo = 'nome' | 'login' | 'cpfCnpj' | 'perfil' | 'senha';

/**
 * Gaveta lateral de inclusão de usuário (RF25). Valida antes de enviar (nome, login único e no formato, CPF/CNPJ com
 * dígitos verificadores, obrigatório para o Operador); o servidor repete tudo e a sua recusa por campo também aparece
 * aqui. O CPF/CNPJ digitado só vai no corpo do POST: nunca é guardado nem exibido de novo.
 */
@Component({
  selector: 'app-novo-usuario',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconeComponent, FocoPresoDirective],
  templateUrl: './novo-usuario.component.html',
  host: { '(document:keydown.escape)': 'fechar.emit()' },
})
export class NovoUsuarioComponent implements OnInit {
  private readonly api = inject(ConfiguracoesApiService);
  private readonly anfitriao = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;

  /** Usuários já cadastrados, para conferir a unicidade do login antes de enviar. */
  readonly logins = input<UsuarioConfigurado[]>([]);
  readonly incluido = output<UsuarioConfigurado>();
  readonly fechar = output<void>();

  protected readonly perfis = PERFIS_ASSINAVEIS;
  protected readonly nome = signal('');
  protected readonly login = signal('');
  protected readonly cpfCnpj = signal('');
  protected readonly perfil = signal('OPERADOR');
  protected readonly senha = signal('');
  protected readonly tentou = signal(false);
  protected readonly enviando = signal(false);
  protected readonly erroGeral = signal('');
  protected readonly erroDoServidor = signal<Record<string, string>>({});

  protected readonly erros = computed<Partial<Record<Campo, string>>>(() => {
    const resultado: Partial<Record<Campo, string>> = {};
    if (!this.nome().trim()) {
      resultado.nome = 'Informe o nome.';
    }
    const login = this.login().trim().toLowerCase();
    if (!login) {
      resultado.login = 'Informe o login.';
    } else if (!LOGIN_VALIDO.test(login)) {
      resultado.login = 'Use de 3 a 60 caracteres: letras, números, ponto, hífen ou sublinhado.';
    } else if (this.logins().some((u) => u.login.toLowerCase() === login)) {
      resultado.login = 'Já existe um usuário com este login.';
    }
    const digitos = somenteDigitos(this.cpfCnpj());
    if (!digitos && this.perfil() === 'OPERADOR') {
      resultado.cpfCnpj = 'O CPF/CNPJ é obrigatório para o Operador.';
    } else if (digitos && !cpfCnpjValido(digitos)) {
      resultado.cpfCnpj = 'CPF/CNPJ inválido.';
    }
    const senha = this.senha();
    if (!senha) {
      resultado.senha = 'Informe a senha inicial.';
    } else if (senha.length < 12) {
      resultado.senha = 'A senha precisa ter pelo menos 12 caracteres.';
    } else if (senha.length > 200) {
      resultado.senha = 'A senha pode ter no máximo 200 caracteres.';
    } else if (!senha.trim()) {
      resultado.senha = 'A senha não pode ser só espaços.';
    } else if (login && senha.toLowerCase().includes(login)) {
      resultado.senha = 'A senha não pode conter o login.';
    }
    return resultado;
  });

  private envio: Subscription | null = null;

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      this.envio?.unsubscribe();
      this.anfitriao.remove();
    });
  }

  ngOnInit(): void {
    // Como as demais gavetas: fica no corpo do documento, acima da barra do topo.
    document.body.appendChild(this.anfitriao);
  }

  protected mensagemDe(campo: Campo): string {
    const local = this.tentou() ? this.erros()[campo] : undefined;
    return local ?? this.erroDoServidor()[campo] ?? '';
  }

  protected digitar(campo: Campo, evento: Event): void {
    const valor = (evento.target as HTMLInputElement | HTMLSelectElement).value;
    this.erroDoServidor.update((atual) => ({ ...atual, [campo]: '' }));
    switch (campo) {
      case 'nome':
        this.nome.set(valor);
        break;
      case 'login':
        this.login.set(valor);
        break;
      case 'perfil':
        this.perfil.set(valor);
        break;
      case 'senha':
        this.senha.set(valor);
        break;
      case 'cpfCnpj': {
        const mascarado = mascararCpfCnpj(valor);
        this.cpfCnpj.set(mascarado);
        (evento.target as HTMLInputElement).value = mascarado;
        break;
      }
    }
  }

  protected aoClicarNoFundo(evento: MouseEvent): void {
    if (evento.target === evento.currentTarget) {
      this.fechar.emit();
    }
  }

  protected incluir(): void {
    this.tentou.set(true);
    this.erroGeral.set('');
    if (Object.keys(this.erros()).length || this.enviando()) {
      return;
    }
    this.enviando.set(true);
    this.envio = this.api
      .incluirUsuario({
        nome: this.nome().trim(),
        login: this.login().trim().toLowerCase(),
        cpfCnpj: somenteDigitos(this.cpfCnpj()),
        perfil: this.perfil(),
        senha: this.senha(),
      })
      .subscribe({
        next: (usuario) => {
          this.enviando.set(false);
          this.senha.set('');
          this.incluido.emit(usuario);
        },
        error: (erro: unknown) => {
          this.enviando.set(false);
          this.senha.set('');
          this.anfitriao.querySelectorAll<HTMLInputElement>('input[type="password"]').forEach((c) => (c.value = ''));
          if (recusaPorCampos(erro)) {
            this.erroDoServidor.set(camposInvalidos(erro));
          } else {
            this.erroGeral.set(mensagemDaConfiguracao(erro));
          }
        },
      });
  }
}
