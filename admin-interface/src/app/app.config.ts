import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import { bankServiceAuthInterceptor } from './core/bank-service-auth.interceptor';
import { sessionExpiryInterceptor } from './core/session-expiry.interceptor';

/**
 * Every screen reads from the Spring Boot API. There is no simulated mode, so a
 * backend that is not running is reported as such instead of being covered by
 * placeholder figures.
 */
export const appConfig: ApplicationConfig = {
  providers: [provideBrowserGlobalErrorListeners(), provideRouter(routes), provideHttpClient(withInterceptors([bankServiceAuthInterceptor, sessionExpiryInterceptor]))],
};
