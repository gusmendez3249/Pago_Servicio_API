import { Injectable, inject } from '@angular/core';
import { ApiClient } from '../../../../core/http/api-client';
import { Result } from '../../../../core/http/result';
import { LoginRequestDto, LoginResponseDto } from '../dto/auth-dto';

@Injectable({ providedIn: 'root' })
export class AuthApi {
  private readonly api = inject(ApiClient);

  login(cuerpo: LoginRequestDto): Promise<Result<LoginResponseDto>> {
    return this.api.post<LoginResponseDto>('/api/v1/auth/login', cuerpo);
  }

  /** El token lo agrega el interceptor; no lleva cuerpo. */
  logout(): Promise<Result<null>> {
    return this.api.post<null>('/api/v1/auth/logout');
  }
}
