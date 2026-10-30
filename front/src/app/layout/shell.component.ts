import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRouteSnapshot, NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';

import { AuthService } from '../core/auth.service';
import { IconeComponent } from '../shared/icone.component';
import { TrilhaService } from '../shared/trilha.service';
import { AcompanhamentoDeDossieService } from '../features/documentos/acompanhamento-dossie.service';
import { GavetaDossieComponent } from '../features/documentos/gaveta-dossie.component';
import { IndicadorDossieComponent } from '../features/documentos/indicador-dossie.component';
import { menuDasPermissoes } from './menu';

@Component({
  selector: 'app-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, IconeComponent, IndicadorDossieComponent, GavetaDossieComponent],
  templateUrl: './shell.component.html',
  host: { '(document:click)': 'menuUsuarioAberto.set(false)', '(document:keydown.escape)': 'menuUsuarioAberto.set(false)' },
})
export class ShellComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly acompanhamento = inject(AcompanhamentoDeDossieService);

  protected readonly usuario = this.auth.usuario;
  protected readonly menu = computed(() => menuDasPermissoes(this.usuario()?.permissoes));
  protected readonly iniciais = computed(() =>
    (this.usuario()?.nome ?? '')
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((parte) => parte[0].toUpperCase())
      .join(''),
  );

  protected readonly recolhido = signal(false);
  protected readonly menuMobileAberto = signal(false);
  protected readonly menuUsuarioAberto = signal(false);
  protected readonly auditoriaAberta = signal(true);
  private readonly complemento = inject(TrilhaService).complemento;
  private readonly trilhaDaRota = signal<string[]>([]);
  protected readonly trilha = computed(() => this.complemento() ?? this.trilhaDaRota());

  constructor() {
    this.trilhaDaRota.set(trilhaDa(this.router.routerState.snapshot.root));
    this.router.events
      .pipe(
        filter((evento) => evento instanceof NavigationEnd),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe(() => {
        this.trilhaDaRota.set(trilhaDa(this.router.routerState.snapshot.root));
        this.menuMobileAberto.set(false);
      });
  }

  protected alternarMenu(): void {
    if (window.innerWidth <= 820) {
      this.menuMobileAberto.update((aberto) => !aberto);
    } else {
      this.recolhido.update((recolhido) => !recolhido);
    }
  }

  protected alternarMenuUsuario(evento: Event): void {
    evento.stopPropagation();
    this.menuUsuarioAberto.update((aberto) => !aberto);
  }

  protected sair(): void {
    this.auth.logout().subscribe(() => void this.router.navigate(['/login']));
  }
}

function trilhaDa(raiz: ActivatedRouteSnapshot): string[] {
  let atual: ActivatedRouteSnapshot | null = raiz;
  let trilha: string[] = [];
  while (atual) {
    const crumb = atual.data['crumb'] as string[] | undefined;
    if (crumb) {
      trilha = crumb;
    }
    atual = atual.firstChild;
  }
  return trilha;
}
