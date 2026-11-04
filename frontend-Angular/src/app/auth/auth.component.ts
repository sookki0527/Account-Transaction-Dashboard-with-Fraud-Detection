import { HttpErrorResponse } from '@angular/common/http';
import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../service/auth.service';

type AuthMode = 'login' | 'signup';

@Component({
  selector: 'app-auth',
  templateUrl: './auth.component.html',
  styleUrls: ['./auth.component.css']
})
export class AuthComponent {
  mode: AuthMode = 'login';
  username = '';
  password = '';
  isSubmitting = false;
  errorMessage = '';
  successMessage = '';

  constructor(private authService: AuthService, private router: Router) {}

  setMode(mode: AuthMode): void {
    this.mode = mode;
    this.errorMessage = '';
    this.successMessage = '';
  }

  submit(): void {
    this.errorMessage = '';
    this.successMessage = '';
    this.isSubmitting = true;

    const request = this.mode === 'signup'
      ? this.authService.register(this.username.trim(), this.password)
      : this.authService.login(this.username.trim(), this.password);

    request.subscribe({
      next: response => {
        this.isSubmitting = false;

        if (this.mode === 'signup') {
          this.password = '';
          this.setMode('login');
    
          this.successMessage = response.message || '회원가입이 완료됐습니다. 로그인해 주세요.';
          return;
        }

        if (!response.token || typeof response.isAdmin !== 'boolean') {
          this.errorMessage = '로그인 응답에 인증 정보가 없습니다.';
          return;
        }

        localStorage.setItem('authToken', response.token);
        localStorage.setItem('userId', this.username.trim());
        localStorage.setItem('isAdmin', String(response.isAdmin));
        void this.router.navigate(['/dashboard']);
      },
      error: (error: HttpErrorResponse) => {
        this.isSubmitting = false;
        this.errorMessage = error.error?.message
          || error.error?.error
          || (this.mode === 'signup'
            ? '회원가입에 실패했습니다. 입력한 정보를 확인해 주세요.'
            : '로그인에 실패했습니다. 아이디와 비밀번호를 확인해 주세요.');
      }
    });
  }
}
