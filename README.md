# Controle de Vendas — Guia de instalação

Este pacote contém o **código-fonte** do app. Ele não roda sozinho: você
precisa criar o projeto no Android Studio e um projeto Firebase (gratuito)
para guardar os dados na nuvem com login Google. São dois passos únicos,
depois é só usar.

## Passo 1 — Instalar o Android Studio
1. Baixe em https://developer.android.com/studio (gratuito).
2. Instale e abra. Na primeira execução, deixe ele baixar os componentes padrão do SDK.

## Passo 2 — Criar o projeto
1. No Android Studio: **New Project > Empty Activity** (a versão "Compose", não a "Views").
2. Nome: `Controle de Vendas`
3. **Package name:** `com.controlefinanceiro.app` (IMPORTANTE: use exatamente esse, o código já está escrito para ele)
4. Minimum SDK: API 26 (Android 8.0) ou superior.
5. Finish e espere o Gradle sincronizar.

## Passo 3 — Criar o projeto Firebase (grátis)
1. Acesse https://console.firebase.google.com e entre com sua conta Google.
2. **Adicionar projeto** → dê um nome → pode desativar o Google Analytics (não é necessário).
3. Dentro do projeto, clique no ícone do Android para **adicionar um app Android**.
4. Package name: `com.controlefinanceiro.app` (o mesmo do passo 2).
5. Baixe o arquivo `google-services.json` e coloque dentro da pasta `app/` do
   seu projeto no Android Studio (mesmo nível do `build.gradle.kts` do módulo app).

## Passo 4 — Ativar login com Google
1. No console Firebase: **Build > Authentication > Sign-in method**.
2. Ative o provedor **Google**.
3. Depois de ativar, clique nele novamente e copie o **"Web client ID"**
   (aparece como "SDK de configuração Web" / "Web SDK configuration").
4. Cole esse ID no arquivo `MainActivity.kt` deste pacote, na constante
   `WEB_CLIENT_ID`, substituindo o texto `COLE_AQUI_SEU_WEB_CLIENT_ID...`.

## Passo 5 — Ativar o banco de dados (Firestore)
1. No console Firebase: **Build > Firestore Database > Criar banco de dados**.
2. Escolha uma região (ex: `southamerica-east1` para Brasil).
3. Comece em **modo de produção** e, na aba **Regras**, cole isto para que
   cada usuário só veja os próprios dados:

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /usuarios/{userId}/{document=**} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
  }
}
```

4. Clique em **Publicar**.

## Passo 6 — Copiar os arquivos de código
1. Copie toda a pasta `app/src/main/java/com/controlefinanceiro/app` deste pacote
   para dentro de `app/src/main/java/com/controlefinanceiro/app` do seu projeto
   no Android Studio (substituindo o `MainActivity.kt` padrão).
2. Substitua também o `app/src/main/AndroidManifest.xml`.
3. Abra `DEPENDENCIAS_GRADLE.txt` e siga as instruções para adicionar as
   dependências do Firebase nos arquivos `build.gradle.kts`.
4. Clique em **Sync Now** quando o Android Studio pedir.

## Passo 7 — Rodar
1. Conecte um celular Android via USB (com "Depuração USB" ativada nas
   opções de desenvolvedor) ou crie um emulador no Android Studio.
2. Clique em **Run ▶**.
3. Na primeira tela, toque em "Entrar com Google" e escolha sua conta.

## O que o app já faz
- Cadastro de clientes (nome, telefone, e-mail, endereço).
- Cadastro de compras com parcelamento: você define quantas parcelas e o
  dia fixo de vencimento (ex: dia 15). As parcelas são geradas automaticamente
  sempre nesse dia, mês a mês, corrigindo meses mais curtos (ex: fevereiro).
- Tela de pagamento: digite o valor pago e o app seleciona automaticamente
  as parcelas mais antigas em aberto até completar o valor — mas você pode
  clicar nos checkboxes para trocar quais parcelas entram no pagamento.
- Pagamento parcial: se o valor não cobrir a parcela inteira, ela fica com
  status "parcialmente paga" e continua em aberto pelo valor restante.
- Toda compra guarda um histórico de pagamentos com descrição automática
  (valor pago + quais parcelas foram quitadas/abatidas).
- Dados salvos no Firestore, isolados por conta Google (cada usuário só
  vê os próprios clientes/compras) — plano gratuito (Spark) é suficiente
  para uso comercial de pequeno/médio porte.

## Possíveis próximos passos (posso ajudar depois)
- Ícone do app e nome de exibição personalizados.
- Filtro de parcelas vencidas/atrasadas e notificações.
- Exportar relatório em PDF/Excel.
- Publicar na Play Store (taxa única de US$25 na conta de desenvolvedor Google).
