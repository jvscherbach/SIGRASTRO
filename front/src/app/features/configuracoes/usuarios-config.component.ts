import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { Subscription } from 'rxjs';

import { AuthService } from '../../core/auth.service';
import { mensagemDoErro } from '../../core/mensagens-erro';
import { formatarDataHora } from '../../shared/formato';
import { IconeComponent } from '../../shared/icone.component';
import { ToastService } from '../../shared/toast.service';
import { ConfiguracoesApiService } from './configuracoes-api.service';
import { PERFIS_ASSINAVEIS, UsuarioConfigurado } from './configuracoes.models';
import { mensagemDaConfiguracao } from './mensagens-config';
import { NovoUsuarioComponent } from './novo-usuario.component';

/** Mudança escolhida na lista e ainda não confirmada. */
interface MudancaPendente {
  id: string;
  perfil?: string;
  ativo?: boolean;
  /** O controle que o usuário mexeu, para voltar ao valor anterior se ele desistir ou o servidor recusar. */
  controle: HTMLSelectElement;
  valorAnterior: string;
}

/**
 * Aba "Usuários e perfis" (RF25, docs/12): perfil e situação são alterados direto na lista, com uma confirmação curta,
 * e gravados na hora (um evento USUARIO por alteração). As proteções (último Administrador, próprio usuário) são do
 * servidor; a tela mostra a mensagem dele. A inclusão é uma gaveta lateral.
 */
@Component({
  selector: 'app-usuarios-config',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconeComponent, NovoUsuarioComponent],
  templateUrl: './usuarios-config.component.html',
})
export class UsuariosConfigComponent implements OnInit {
  private readonly api = inject(ConfiguracoesApiService);
  private readonly toast = inject(ToastService);
  private readonly auth = inject(AuthService);

  protected readonly perfis = PERFIS_ASSINAVEIS;
  protected readonly formatarDataHora = formatarDataHora;

  protected readonly carregando = signal(true);
  protected readonly erro = signal('');
  protected readonly usuarios = signal<UsuarioConfigurado[]>([]);
  protected readonly gaveta = signal(false);
  protected readonly pendente = signal<MudancaPendente | null>(null);
  protected readonly gravando = signal(false);
  /** Erro da última alteração recusada, na linha do usuário. */
  protected readonly erroDaLinha = signal<{ id: string; texto: string } | null>(null);
  protected readonly eu = computed(() => this.auth.usuario()?.login ?? null);

  private carga: Subscription | null = null;
  private gravacao: Subscription | null = null;

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      this.carga?.unsubscribe();
      this.gravacao?.unsubscribe();
    });
  }

  ngOnInit(): void {
    this.carregar();
  }

  protected carregar(): void {
    this.carga?.unsubscribe();
    this.carregando.set(true);
    this.erro.set('');
    this.carga = this.api.usuarios().subscribe({
      next: (pagina) => {
        this.usuarios.set(pagina.conteudo);
        this.carregando.set(false);
      },
      error: (erro: unknown) => {
        this.carregando.set(false);
        this.erro.set(erro instanceof HttpErrorResponse ? mensagemDoErro(erro) : 'Não foi possível carregar os usuários.');
      },
    });
  }

  // --- alteração direto na lista

  protected escolherPerfil(usuario: UsuarioConfigurado, evento: Event): void {
    const controle = evento.target as HTMLSelectElement;
    if (controle.value === usuario.perfil) {
      return;
    }
    this.desfazerPendente();
    this.erroDaLinha.set(null);
    this.pendente.set({ id: usuario.id, perfil: controle.value, controle, valorAnterior: usuario.perfil });
  }

  protected escolherSituacao(usuario: UsuarioConfigurado, evento: Event): void {
    const controle = evento.target as HTMLSelectElement;
    const ativo = controle.value === 'ativo';
    if (ativo === usuario.ativo) {
      return;
    }
    this.desfazerPendente();
    this.erroDaLinha.set(null);
    this.pendente.set({ id: usuario.id, ativo, controle, valorAnterior: usuario.ativo ? 'ativo' : 'inativo' });
  }

  protected descricaoDaMudanca(usuario: UsuarioConfigurado, mudanca: MudancaPendente): string {
    if (mudanca.perfil !== undefined) {
      const rotulo = this.perfis.find((p) => p.codigo === mudanca.perfil)?.rotulo ?? mudanca.perfil;
      return `Mudar o perfil de ${usuario.nome} para ${rotulo}?`;
    }
    return mudanca.ativo ? `Reativar ${usuario.nome}?` : `Desativar ${usuario.nome}? Ele deixa de entrar no sistema.`;
  }

  protected cancelarMudanca(): void {
    this.desfazerPendente();
    this.pendente.set(null);
  }

  private desfazerPendente(): void {
    const atual = this.pendente();
    if (atual) {
      atual.controle.value = atual.valorAnterior;
    }
  }

  protected confirmarMudanca(): void {
    const mudanca = this.pendente();
    if (!mudanca || this.gravando()) {
      return;
    }
    this.gravando.set(true);
    const alteracao = mudanca.perfil !== undefined ? { perfil: mudanca.perfil } : { ativo: mudanca.ativo };
    this.gravacao = this.api.alterarUsuario(mudanca.id, alteracao).subscribe({
      next: (atualizado) => {
        this.gravando.set(false);
        this.pendente.set(null);
        this.usuarios.update((lista) => lista.map((u) => (u.id === atualizado.id ? atualizado : u)));
        this.toast.info(`Usuário ${atualizado.nome} atualizado.`);
      },
      error: (erro: unknown) => {
        this.gravando.set(false);
        // volta o controle ao valor anterior: a tela só mostra o que o servidor gravou
        mudanca.controle.value = mudanca.valorAnterior;
        this.pendente.set(null);
        this.erroDaLinha.set({ id: mudanca.id, texto: mensagemDaConfiguracao(erro) });
      },
    });
  }

  // --- inclusão

  protected abrirGaveta(): void {
    this.cancelarMudanca();
    this.gaveta.set(true);
  }

  protected fecharGaveta(): void {
    this.gaveta.set(false);
  }

  protected aoIncluir(novo: UsuarioConfigurado): void {
    this.gaveta.set(false);
    this.usuarios.update((lista) => [...lista, novo].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR')));
    this.toast.info(`Usuário ${novo.nome} incluído.`);
  }
}
