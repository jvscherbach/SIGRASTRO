import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { DomSanitizer } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';

import { AuthService } from '../../core/auth.service';
import { IconeComponent } from '../../shared/icone.component';
import { ToastService } from '../../shared/toast.service';
import { cenaLogin } from './cena-login';

/** Senha de demonstração do perfil local (rastro.demo.senha). Só existe para o clique rápido do protótipo. */
const SENHA_DEMO = 'vistas-demo';

interface UsuarioDemo {
  login: string;
  nome: string;
  iniciais: string;
  descricao: string;
}

const USUARIOS_DEMO: UsuarioDemo[] = [
  { login: 'operador.demo', nome: 'Operador Demo', iniciais: 'OD', descricao: 'Operador · só processos em que é o interessado' },
  { login: 'gestor.demo', nome: 'Gestor Demo', iniciais: 'GD', descricao: 'Gestor · todos os processos e sigilosos' },
  { login: 'admin.demo', nome: 'Admin Demo', iniciais: 'AD', descricao: 'Administrador · acesso irrestrito' },
];

@Component({
  selector: 'app-login',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, IconeComponent],
  templateUrl: './login.component.html',
})
export class LoginComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly rota = inject(ActivatedRoute);
  private readonly toast = inject(ToastService);
  private readonly sanitizer = inject(DomSanitizer);

  protected readonly usuariosDemo = USUARIOS_DEMO;
  protected readonly erro = signal('');
  protected readonly entrando = signal(false);

  protected readonly formulario = inject(FormBuilder).nonNullable.group({
    usuario: ['', [Validators.required, Validators.maxLength(100)]],
    senha: ['', [Validators.required, Validators.maxLength(200)]],
  });

  // SVG estático gerado pelo próprio código (cena do protótipo), por isso é seguro marcá-lo como confiável.
  protected readonly cena = computed(() => this.sanitizer.bypassSecurityTrustHtml(cenaLogin()));

  protected entrar(): void {
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      this.erro.set('Informe o usuário e a senha.');
      return;
    }
    const { usuario, senha } = this.formulario.getRawValue();
    this.autenticar(usuario, senha);
  }

  protected entrarComoDemo(demo: UsuarioDemo): void {
    this.formulario.setValue({ usuario: demo.login, senha: SENHA_DEMO });
    this.autenticar(demo.login, SENHA_DEMO, true);
  }

  private autenticar(usuario: string, senha: string, demo = false): void {
    this.erro.set('');
    this.entrando.set(true);
    this.auth.login(usuario, senha).subscribe({
      next: (u) => {
        this.toast.info(`Acesso simulado como ${u.nome}.`);
        const destino = this.rota.snapshot.queryParamMap.get('retorno');
        void this.router.navigateByUrl(destino && destino.startsWith('/') ? destino : '/consulta');
      },
      error: (erro: unknown) => {
        this.entrando.set(false);
        if (erro instanceof HttpErrorResponse && erro.status === 401) {
          this.formulario.controls.senha.reset('');
          this.erro.set(
            demo
              ? 'A senha de demonstração foi alterada no servidor. Informe a senha configurada em VISTAS_DEMO_SENHA.'
              : 'Usuário ou senha inválidos.',
          );
        }
      },
    });
  }
}
