import { Injectable, inject } from '@angular/core';

import { PREFIXO, lerComMigracao } from '../core/armazenamento';
import { AuthService } from '../core/auth.service';

/**
 * Atalho de consulta recente. `numeroRequerimento` e `situacao` desempatam atalhos de mesmo rótulo (um processo com
 * dois requerimentos); atalhos antigos, só com id e rótulo, continuam valendo.
 */
export interface Recente {
  id: number;
  rotulo: string;
  numeroRequerimento?: string;
  situacao?: string;
}

/** Como o atalho aparece: o título e, quando há outro atalho com o mesmo rótulo, a situação em texto pequeno. */
export interface RecenteExibido {
  id: number;
  titulo: string;
  situacao: string | null;
}

const MAXIMO = 6;

/**
 * Consultas recentes de cada usuário, no localStorage do navegador. Guarda só o id, o rótulo ("Processo 2825/2025"),
 * o número do requerimento e a situação; nenhum dado do interessado.
 */
@Injectable({ providedIn: 'root' })
export class RecentesService {
  private readonly auth = inject(AuthService);

  listar(): Recente[] {
    const chave = this.nomeDaChave();
    if (!chave) {
      return [];
    }
    try {
      const itens = JSON.parse(lerComMigracao(localStorage, chave) ?? '[]') as unknown;
      return Array.isArray(itens) ? itens.filter(ehRecente).map(normalizar).slice(0, MAXIMO) : [];
    } catch {
      return [];
    }
  }

  /** Atalhos prontos para exibir; rótulos repetidos ganham "· Req. N/AAAA" e a situação. */
  listarParaExibicao(): RecenteExibido[] {
    const lista = this.listar();
    const repetidos = new Set(lista.map((r) => r.rotulo).filter((rotulo, i, todos) => todos.indexOf(rotulo) !== i));
    return lista.map((r) => {
      const desempata = repetidos.has(r.rotulo);
      return {
        id: r.id,
        titulo: desempata && r.numeroRequerimento ? `${r.rotulo} · Req. ${r.numeroRequerimento}` : r.rotulo,
        situacao: desempata ? (r.situacao ?? null) : null,
      };
    });
  }

  registrar(recente: Recente): void {
    const chave = this.nomeDaChave();
    if (!chave) {
      return;
    }
    const lista = [recente, ...this.listar().filter((r) => r.id !== recente.id)].slice(0, MAXIMO);
    try {
      localStorage.setItem(PREFIXO + chave, JSON.stringify(lista));
    } catch {
      // sem localStorage (modo privado, cota): a lista de recentes simplesmente não é guardada.
    }
  }

  /** Nome sem prefixo (`recentes.<login>`); a chave é `rastro.recentes.<login>` (antes `vistas.`, D75). */
  private nomeDaChave(): string | null {
    const login = this.auth.usuario()?.login;
    return login ? `recentes.${login}` : null;
  }
}

function ehRecente(valor: unknown): valor is Recente {
  const r = valor as Recente | null;
  return typeof r?.id === 'number' && typeof r?.rotulo === 'string';
}

/** Só os campos conhecidos, e só quando são texto: lixo no localStorage não chega à tela. */
function normalizar(r: Recente): Recente {
  const recente: Recente = { id: r.id, rotulo: r.rotulo };
  if (typeof r.numeroRequerimento === 'string') {
    recente.numeroRequerimento = r.numeroRequerimento;
  }
  if (typeof r.situacao === 'string') {
    recente.situacao = r.situacao;
  }
  return recente;
}
