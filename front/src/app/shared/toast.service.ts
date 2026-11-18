import { Injectable, signal } from '@angular/core';

/** Ação opcional do aviso (ex.: "Abrir"); clicar nela também fecha o aviso. */
export interface AcaoDoToast {
  rotulo: string;
  aoClicar: () => void;
}

export interface Toast {
  id: number;
  texto: string;
  tipo: 'info' | 'erro';
  acao?: AcaoDoToast;
}

@Injectable({ providedIn: 'root' })
export class ToastService {
  private proximoId = 1;
  private readonly lista = signal<Toast[]>([]);

  readonly toasts = this.lista.asReadonly();

  /** Com ação, o aviso fica mais tempo na tela para dar chance de clicar. */
  info(texto: string, acao?: AcaoDoToast): void {
    this.mostrar(texto, 'info', acao ? 10_000 : 3200, acao);
  }

  erro(texto: string): void {
    this.mostrar(texto, 'erro', 6000);
  }

  fechar(id: number): void {
    this.lista.update((atual) => atual.filter((t) => t.id !== id));
  }

  executar(toast: Toast): void {
    this.fechar(toast.id);
    toast.acao?.aoClicar();
  }

  private mostrar(texto: string, tipo: Toast['tipo'], duracaoMs: number, acao?: AcaoDoToast): void {
    const id = this.proximoId++;
    this.lista.update((atual) => [...atual.slice(-2), { id, texto, tipo, acao }]);
    setTimeout(() => this.fechar(id), duracaoMs);
  }
}
