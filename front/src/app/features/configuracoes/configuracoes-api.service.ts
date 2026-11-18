import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { SEM_TOAST } from '../../core/interceptors';
import { PageResponse } from '../../shared/api-tipos';
import {
  AlteracaoDeUsuario,
  Conexao,
  NovoUsuario,
  ParametrosResposta,
  TesteDeConexao,
  UsuarioConfigurado,
  ValorDeParametro,
} from './configuracoes.models';

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

  // --- parâmetros e conexões (S5, bloco 8)

  parametros(): Observable<ParametrosResposta> {
    return this.http.get<ParametrosResposta>('/api/configuracoes/parametros', { context: SEM_AVISO() });
  }

  /** Todas as alterações pendentes de uma vez: o servidor aplica todas ou nenhuma e gera um único evento. */
  salvarParametros(alteracoes: Record<string, ValorDeParametro>): Observable<ParametrosResposta> {
    return this.http.put<ParametrosResposta>('/api/configuracoes/parametros', { alteracoes }, { context: SEM_AVISO() });
  }

  restaurarParametro(nome: string): Observable<ParametrosResposta> {
    return this.http.delete<ParametrosResposta>(`/api/configuracoes/parametros/${encodeURIComponent(nome)}`, {
      context: SEM_AVISO(),
    });
  }

  conexoes(): Observable<{ conexoes: Conexao[] }> {
    return this.http.get<{ conexoes: Conexao[] }>('/api/configuracoes/conexoes', { context: SEM_AVISO() });
  }

  testarConexao(nome: string): Observable<TesteDeConexao> {
    return this.http.post<TesteDeConexao>(`/api/configuracoes/conexoes/${encodeURIComponent(nome)}/teste`, null, {
      context: SEM_AVISO(),
    });
  }
}
