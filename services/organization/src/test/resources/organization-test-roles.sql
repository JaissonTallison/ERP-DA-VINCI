-- Papel da aplicação nos testes: sem privilégio de dono, para o Row-Level Security valer como em produção.
CREATE ROLE organization_app LOGIN PASSWORD 'organization_app_test';
