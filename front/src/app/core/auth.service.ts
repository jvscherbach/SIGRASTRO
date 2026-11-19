import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, map, of, tap } from 'rxjs';

import { PREFIXO, lerComMigracao, removerComAntiga } from './armazenamento';
import { LoginResposta, Usuario } from './usuario.model';

const NOME_TOKEN = 'token';
const CHAVE_TOKEN = PREFIXO + NOME_TOKEN;

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  private readonly usuarioAtual = signal<Usuario | null>(null);

  readonly usuario = this.usuarioAtual.asReadonly();
  readonly autenticado = computed(() => this.usuarioAtual() !== null);

  token(): string | null {
    try {
      return lerComMigracao(sessionStorage, NOME_TOKEN);
    } catch {
      return null;
    }
  }

  login(usuario: string, senha: string): Observable<Usuario> {
    return this.http.post<LoginResposta>('/api/auth/login', { usuario, senha }).pipe(
      tap((resposta) => {
        this.guardarToken(resposta.token);
        this.usuarioAtual.set(resposta.usuario);
      }),
      map((resposta) => resposta.usuario),
    );
  }

  /** Revalida a sessão guardada (ex.: após recarregar a página). Emite false se não houver sessão válida. */
  restaurar(): Observable<boolean> {
    if (!this.token()) {
      return of(false);
    }
    return this.http.get<Usuario>('/api/auth/me').pipe(
      tap((usuario) => this.usuarioAtual.set(usuario)),
      map(() => true),
      catchError(() => {
        this.limparSessao();
        return of(false);
      }),
    );
  }

  logout(): Observable<void> {
    const encerrar = this.token()
      ? this.http.post<void>('/api/auth/logout', null).pipe(catchError(() => of(undefined)))
      : of(undefined);
    return encerrar.pipe(
      tap(() => this.limparSessao()),
      map(() => undefined),
    );
  }

  limparSessao(): void {
    this.usuarioAtual.set(null);
    try {
      removerComAntiga(sessionStorage, NOME_TOKEN);
    } catch {
      // sessionStorage indisponível: nada a limpar.
    }
  }

  private guardarToken(token: string): void {
    try {
      sessionStorage.setItem(CHAVE_TOKEN, token);
    } catch {
      // sem sessionStorage a sessão vale só até recarregar a página.
    }
  }
}
