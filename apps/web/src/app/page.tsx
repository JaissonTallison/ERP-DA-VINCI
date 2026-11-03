import Image from 'next/image';

export default function HomePage() {
  return (
    <>
      <header className="bg-surface shadow-bar">
        <div className="flex h-18 items-center px-4 sm:px-6">
          <Image
            src="/brand/logo.svg"
            alt="daVINCI ERP Enterprise"
            width={160}
            height={60}
            priority
            unoptimized
          />
        </div>
      </header>
      <main className="px-4 py-6 sm:px-6">
        <h1 className="text-3xl font-bold text-brand">Fundação da plataforma</h1>
        <section className="mt-6 rounded-md border border-border bg-surface p-5 shadow-card">
          <h2 className="text-xl font-bold">Fase 1 em andamento</h2>
          <p className="mt-2 text-text-muted">
            Estrutura técnica, padrões de engenharia e arquitetura global. Nenhum módulo de negócio
            está disponível nesta fase.
          </p>
        </section>
      </main>
    </>
  );
}
