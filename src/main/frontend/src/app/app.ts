import { Component, inject, OnInit } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

/**
 * Root application shell. Phase 5 baseline: bootstraps the router outlet and
 * resolves the user's preferred dialect from `navigator.language` per the
 * fallback rules in SR-00-C18.F01.
 */
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, TranslateModule],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App implements OnInit {
  private readonly translate = inject(TranslateService);

  // Dialects shipped per UR-00-C18 / SR-00-C18.F01.
  private static readonly SHIPPED_DIALECTS = ['en-GB', 'de-DE', 'fr-FR', 'es-ES'];
  private static readonly DEFAULT_DIALECT = 'en-GB';

  ngOnInit(): void {
    this.translate.addLangs(App.SHIPPED_DIALECTS);
    this.translate.use(this.resolveActiveDialect(navigator.language));
  }

  /**
   * Resolves the requested locale tag against the shipped dialects per
   * SR-00-C18.F01:
   *   - exact tag match (e.g., `en-GB`) wins;
   *   - same-language-different-region falls back to the shipped dialect
   *     (e.g., `en-US` → `en-GB`, `de-CH` → `de-DE`);
   *   - otherwise `en-GB`.
   */
  private resolveActiveDialect(requested: string): string {
    if (App.SHIPPED_DIALECTS.includes(requested)) {
      return requested;
    }
    const languageRoot = requested.split('-')[0]?.toLowerCase() ?? '';
    const fallback = App.SHIPPED_DIALECTS.find(
      tag => tag.split('-')[0]?.toLowerCase() === languageRoot,
    );
    return fallback ?? App.DEFAULT_DIALECT;
  }
}
