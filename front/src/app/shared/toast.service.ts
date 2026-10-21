import { Injectable, signal } from '@angular/core';

export interface Toast {
  id: number;
  texto: string;
  tipo: 'info' | 'erro';
}

@Injectable({ providedIn: 'root' })
export class ToastService {
  private proximoId = 1;
  private readonly lista = signal<Toast[]>([]);

  readonly toasts = this.lista.asReadonly();

  info(texto: string): void {
    this.mostrar(texto, 'info', 3200);
  }

  erro(texto: string): void {
    this.mostrar(texto, 'erro', 6000);
  }

  fechar(id: number): void {
    this.lista.update((atual) => atual.filter((t) => t.id !== id));
  }

  private mostrar(texto: string, tipo: Toast['tipo'], duracaoMs: number): void {
    const id = this.proximoId++;
    this.lista.update((atual) => [...atual.slice(-2), { id, texto, tipo }]);
    setTimeout(() => this.fechar(id), duracaoMs);
  }
}
