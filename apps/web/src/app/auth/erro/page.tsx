import type { Metadata } from 'next';
import { TopBar } from '@/components/top-bar';

export const metadata: Metadata = { title: 'Acesso não liberado' };

type Message = { title: string; detail: string };

const LOGIN_FAILED: Message = {
  title: 'Não foi possível concluir o login.',
  detail: 'O link de login expirou ou foi aberto em outro navegador. Tente entrar novamente.',
};

const MESSAGES: Record<string, Message> = {
  'sem-organizacao': {
    title: 'Sua conta não está vinculada a uma empresa ativa.',
    detail:
      'Se a empresa foi suspensa ou o acesso ainda está sendo liberado, fale com o administrador da sua empresa.',
  },
  'varias-organizacoes': {
    title: 'Sua conta está vinculada a mais de uma empresa.',
    detail: 'A escolha de empresa no login ainda não está disponível. Fale com o suporte.',
  },
  login: LOGIN_FAILED,
};

export default async function AuthErrorPage({
  searchParams,
}: {
  searchParams: Promise<{ motivo?: string }>;
}) {
  const { motivo } = await searchParams;
  const message = MESSAGES[motivo ?? ''] ?? LOGIN_FAILED;

  return (
    <>
      <TopBar />
      <main className="px-4 py-6 sm:px-6">
        <section className="max-w-xl rounded-md border border-border bg-surface p-5 shadow-card">
          <h1 className="text-xl font-bold text-brand">{message.title}</h1>
          <p className="mt-2 text-text-muted">{message.detail}</p>
          <a
            href="/auth/login?prompt=login"
            className="mt-5 inline-flex h-field items-center rounded-md bg-primary px-4 text-sm font-semibold text-surface hover:bg-primary-hover"
          >
            Entrar com outra conta
          </a>
        </section>
      </main>
    </>
  );
}
