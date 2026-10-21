import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { SpriteIconesComponent } from './shared/sprite-icones.component';
import { ToastComponent } from './shared/toast.component';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, SpriteIconesComponent, ToastComponent],
  template: `
    <app-sprite-icones />
    <router-outlet />
    <app-toast />
  `,
})
export class App {}
