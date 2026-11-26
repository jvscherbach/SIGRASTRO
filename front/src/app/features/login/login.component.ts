import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { AuthService } from '../../core/auth.service';
import { destinoInicial } from '../../core/destino';
import { mensagemDoBloqueio } from '../../core/mensagens-erro';
import { ProblemDetail } from '../../core/usuario.model';
import { IconeComponent } from '../../shared/icone.component';
import { MarcaComponent } from '../../shared/marca.component';

@Component({
  selector: 'app-login',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, IconeComponent, MarcaComponent],
  templateUrl: './login.component.html',
})
export class LoginComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly rota = inject(ActivatedRoute);

  protected readonly erro = signal('');
  protected readonly entrando = signal(false);

  protected readonly formulario = inject(FormBuilder).nonNullable.group({
    usuario: ['', [Validators.required, Validators.maxLength(100)]],
    senha: ['', [Validators.required, Validators.maxLength(200)]],
  });

  protected entrar(): void {
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      this.erro.set('Informe o usuário e a senha.');
      return;
    }
    const { usuario, senha } = this.formulario.getRawValue();
    this.erro.set('');
    this.entrando.set(true);
    this.auth.login(usuario, senha).subscribe({
      next: (u) => {
        const destino = this.rota.snapshot.queryParamMap.get('retorno');
        void this.router.navigateByUrl(destino && destino.startsWith('/') ? destino : destinoInicial(u));
      },
      error: (erro: unknown) => {
        this.entrando.set(false);
        this.formulario.controls.senha.reset('');
        if (erro instanceof HttpErrorResponse && erro.status === 429) {
          const problema = erro.error as (ProblemDetail & { segundosRestantes?: number }) | null;
          this.erro.set(mensagemDoBloqueio(problema?.segundosRestantes));
        } else if (erro instanceof HttpErrorResponse && erro.status === 401) {
          this.erro.set('Usuário ou senha inválidos.');
        } else {
          this.erro.set('Não foi possível entrar agora. Tente novamente.');
        }
      },
    });
  }
}
