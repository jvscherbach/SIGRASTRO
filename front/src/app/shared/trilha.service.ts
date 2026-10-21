import { Injectable, signal } from '@angular/core';

/** Permite a uma tela completar a trilha do topo com algo que só ela conhece (ex.: "Processo 2825/2025"). */
@Injectable({ providedIn: 'root' })
export class TrilhaService {
  private readonly atual = signal<string[] | null>(null);

  readonly complemento = this.atual.asReadonly();

  definir(trilha: string[]): void {
    this.atual.set(trilha);
  }

  limpar(): void {
    this.atual.set(null);
  }
}
