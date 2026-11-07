# 💰 Mão Fechada

Controle de gastos pessoais pelo navegador e pelo Telegram. Envie uma mensagem como **“gastei R$ 32,50 no almoço”**, confira a interpretação do bot e confirme para ver o lançamento no dashboard.

<p align="center">
  <img src="docs/images/telegram.png" alt="Bot do Mão Fechada confirmando um gasto no Telegram" height="440">
  <img src="docs/images/dashboard.png" alt="Dashboard do Mão Fechada no celular" height="440">
</p>

## O que o projeto faz

- Registra, edita e exclui receitas e despesas, com histórico e saldo atualizado.
- Organiza lançamentos em categorias e acompanha metas mensais de despesa.
- Mostra resumo do mês, comparação com o mês anterior e gastos por categoria.
- Interpreta mensagens pelo Gemini no Telegram e pede confirmação antes de salvar.
- Abre o dashboard como Telegram Mini App, com validação dos dados assinados pelo Telegram.
- Mantém os dados de cada conta isolados por autenticação JWT.

## Tecnologias

| Camada | Tecnologias |
| --- | --- |
| Frontend | React, TypeScript, Vite, CSS, Lucide |
| API | Java 21, Spring Boot 4, Spring Security, Spring Data JPA, MapStruct |
| Dados | PostgreSQL 17 |
| Integrações | Telegram Bot API, Gemini API |

## Executar localmente

Você precisa de **Docker Compose**, **Node.js** e **npm**. Na raiz do projeto, inicie a API e o PostgreSQL:

```bash
docker compose up --build -d
```

Em outro terminal, inicie a interface:

```bash
cd frontend
npm install
npm run dev
```

Acesse **http://localhost:5173**. O Vite encaminha `/api` para a API em `http://localhost:8080`. Se ela estiver em outra porta, use `CDG_API_PROXY=http://localhost:8081 npm run dev`. Para uma API pública, configure `VITE_API_URL` conforme o [README do frontend](frontend/README.md).

Para executar a API sem Docker, use Java 21 e PostgreSQL, defina `DB_PASSWORD` e `JWT_SECRET` e rode `bash mvnw spring-boot:run`. As variáveis `DB_HOST`, `DB_PORT`, `DB_NAME` e `DB_USER` permitem alterar a conexão padrão.

### Integrar com o Telegram

Defina estas variáveis no ambiente da API antes de iniciá-la:

| Variável | Uso |
| --- | --- |
| `TELEGRAM_BOT_TOKEN` | Token do bot |
| `TELEGRAM_WEBHOOK_SECRET` | Segredo enviado pelo Telegram ao webhook |
| `TELEGRAM_BOT_USERNAME` | Nome do bot sem `@`, para gerar o link de conexão |
| `GEMINI_API_KEY` | Chave da API Gemini para interpretar mensagens |
| `CDG_WEB_URL` | URL HTTPS pública do frontend, sem barra final |

`GEMINI_MODEL` é opcional e aceita modelos separados por vírgula. A API tenta o próximo em falhas temporárias ou de conexão. Configure no Telegram o webhook público `POST /integracoes/telegram/webhook` com `secret_token` igual a `TELEGRAM_WEBHOOK_SECRET`.

No dashboard, abra **Telegram → Conectar Telegram**. Use **Abrir no Telegram** ou envie `/start <código>` ao bot em uma conversa privada; o código expira em 10 minutos. Depois, envie um gasto ou receita e toque em **Confirmar** para salvar, ou **Cancelar** para descartar. O botão **Abrir dashboard** no bot abre a Mini App.

Se o bot já tiver criado uma conta sem e-mail, os lançamentos dela passam para a conta vinculada. Se ele estava ligado a outra conta com e-mail, os dados antigos permanecem nela e os novos lançamentos passam a entrar na conta recém-vinculada.

## API

| Método | Rota | Função |
| --- | --- | --- |
| `POST` | `/auth/register`, `/auth/login` | Cadastro e login |
| `POST` | `/auth/telegram/webapp` | Login na Mini App |
| `GET`, `POST` | `/integracoes/telegram/vinculo` | Consultar vínculo e gerar código |
| `POST` | `/integracoes/telegram/webhook` | Receber mensagens do bot |
| `GET` | `/transacoes`, `/transacoes/{id}`, `/transacoes/periodo`, `/transacoes/dashboard`, `/transacoes/resumo?mes=AAAA-MM` | Histórico paginado, lançamento e resumos |
| `POST` | `/transacoes` | Criar lançamento |
| `PUT`, `DELETE` | `/transacoes/{id}` | Editar e excluir lançamento |
| `GET`, `POST` | `/categorias` | Listar e criar categorias |
| `PUT`, `DELETE` | `/categorias/{id}` | Editar e excluir categoria |

Ao excluir uma categoria com lançamentos, informe `?moverPara={id}` para movê-los a outra categoria do mesmo tipo. Valores monetários são enviados pela API como texto decimal para preservar os centavos.

`GET /transacoes?pagina=0&tamanho=20` retorna `{ "itens": [...], "temMais": true/false }`, em ordem de data e ID decrescente. `tamanho` aceita de 1 a 50. `GET /transacoes/{id}` consulta um lançamento da conta autenticada; `GET /transacoes/periodo?inicio=AAAA-MM-DD&fim=AAAA-MM-DD` retorna os lançamentos de até 31 dias e indica se existem dias anteriores.

## Banco de dados

O Flyway aplica as migrações em `src/main/resources/db/migration` antes de iniciar a API. Em um banco vazio, ele cria o esquema completo. Em um banco já criado pelas versões anteriores, a primeira execução registra a versão 1 como base e aplica a partir da versão 2; faça um backup antes do primeiro deploy dessa mudança. O Hibernate valida o esquema na inicialização e não o altera automaticamente.

## Testes

```bash
bash mvnw test
cd frontend && npm test
```

Os testes de inicialização, confirmação concorrente e paginação precisam do PostgreSQL ativo.

## Autor

**Hanrry Santos** · [GitHub](https://github.com/hanrrysantos) · [LinkedIn](https://www.linkedin.com/in/hanrrysantos)

Projeto de estudo e portfólio.
