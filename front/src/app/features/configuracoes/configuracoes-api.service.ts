import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { SEM_TOAST } from '../../core/interceptors';
import { PageResponse } from '../../shared/api-tipos';
import { AlteracaoDeUsuario, NovoUsuario, UsuarioConfigurado } from './configuracoes.models';

/** A tela mostra o erro no próprio corpo, sem toast duplicado. */
const SEM_AVISO = () => new HttpContext().set(SEM_TOAST, true);

@Injectable({ providedIn: 'root' })
export class ConfiguracoesApiService {
  private readonly http = inject(HttpClient);

  usuarios(): Observable<PageResponse<UsuarioConfigurado>> {
    return this.http.get<PageResponse<UsuarioConfigurado>>('/api/configuracoes/usuarios', { context: SEM_AVISO() });
  }

  incluirUsuario(usuario: NovoUsuario): Observable<UsuarioConfigurado> {
    return this.http.post<UsuarioConfigurado>('/api/configuracoes/usuarios', usuario, { context: SEM_AVISO() });
  }

  alterarUsuario(id: string, alteracao: AlteracaoDeUsuario): Observable<UsuarioConfigurado> {
    return this.http.patch<UsuarioConfigurado>(`/api/configuracoes/usuarios/${encodeURIComponent(id)}`, alteracao, {
      context: SEM_AVISO(),
    });
  }
}
