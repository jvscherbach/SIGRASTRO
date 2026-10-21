import { Injectable, inject } from '@angular/core';

import { AuthService } from '../core/auth.service';

export interface Recente {
  id: number;
  rotulo: string;
}

const MAXIMO = 6;

/**
 * Consultas recentes de cada usuário, no localStorage do navegador. Guarda só o id e o rótulo
 * ("Processo 2825/2025"); nenhum dado do interessado.
 */
@Injectable({ providedIn: 'root' })
export class RecentesService {
  private readonly auth = inject(AuthService);

  listar(): Recente[] {
    const chave = this.chave();
    if (!chave) {
      return [];
    }
    try {
      const itens = JSON.parse(localStorage.getItem(chave) ?? '[]') as unknown;
      return Array.isArray(itens) ? itens.filter(ehRecente).slice(0, MAXIMO) : [];
    } catch {
      return [];
    }
  }

  registrar(recente: Recente): void {
    const chave = this.chave();
    if (!chave) {
      return;
    }
    const lista = [recente, ...this.listar().filter((r) => r.id !== recente.id)].slice(0, MAXIMO);
    try {
      localStorage.setItem(chave, JSON.stringify(lista));
    } catch {
      // sem localStorage (modo privado, cota): a lista de recentes simplesmente não é guardada.
    }
  }

  private chave(): string | null {
    const login = this.auth.usuario()?.login;
    return login ? `vistas.recentes.${login}` : null;
  }
}

function ehRecente(valor: unknown): valor is Recente {
  const r = valor as Recente | null;
  return typeof r?.id === 'number' && typeof r?.rotulo === 'string';
}
