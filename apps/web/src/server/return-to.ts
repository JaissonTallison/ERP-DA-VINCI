/**
 * Destino depois do login. Só aceita caminhos internos, para não virar redirecionamento aberto
 * (`//evil.com`, `/\evil.com`, URLs absolutas) nem voltar para as próprias rotas de autenticação.
 */
export function safeReturnTo(value: string | null | undefined): string {
  if (!value || !value.startsWith('/') || value.startsWith('//') || value.startsWith('/\\')) {
    return '/';
  }
  if (value.startsWith('/auth/')) {
    return '/';
  }
  return value;
}
