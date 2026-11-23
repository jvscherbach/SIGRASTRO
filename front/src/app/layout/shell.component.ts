import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';

import { AuthService } from '../core/auth.service';
import { IconeComponent } from '../shared/icone.component';
import { MarcaComponent } from '../shared/marca.component';
import { AcompanhamentoDeDossieService } from '../features/documentos/acompanhamento-dossie.service';
import { GavetaDossieComponent } from '../features/documentos/gaveta-dossie.component';
import { IndicadorDossieComponent } from '../features/documentos/indicador-dossie.component';
import { ItemMenu, itemAtivo, menuDasPermissoes } from './menu';

@Component({
  selector: 'app-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, IconeComponent, MarcaComponent, IndicadorDossieComponent, GavetaDossieComponent],
  templateUrl: './shell.component.html',
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

  private readonly url = signal(this.router.url);

  constructor() {
    this.router.events
      .pipe(
        filter((evento) => evento instanceof NavigationEnd),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe((evento) => this.url.set(evento.urlAfterRedirects));
  }

  protected ativo(item: ItemMenu): boolean {
    return itemAtivo(item, this.url());
  }

  protected sair(): void {
    this.auth.logout().subscribe(() => void this.router.navigate(['/login']));
  }
}
