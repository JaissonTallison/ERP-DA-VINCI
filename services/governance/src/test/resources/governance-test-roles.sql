-- Papel da aplicação nos testes: sem privilégio de dono, para o Row-Level Security valer como em produção.
CREATE ROLE governance_app LOGIN PASSWORD 'governance_app_test';
