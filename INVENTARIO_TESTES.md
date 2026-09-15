# Inventário de testes e revisão de código

Atualizado após a correção de `valoresManuaisTexto` em `EntradaScreen.kt`. Suíte total: **250 testes automatizados**, todos passando (sem mudança de contagem nessa rodada — o achado foi em código Compose, sem JUnit possível).

## Suíte de testes automatizados (`app/src/test/java/.../data/`)

| Arquivo | Testes | O que cobre |
|---|---|---|
| `ParcelaUtilsTest.kt` | 45 | Geração de parcelas (dinheiro/cartão), arredondamento, regra de fechamento de fatura, baixa sequencial/proporcional, reversão de baixa, edição de data, ciclo de vida completo |
| `ImportarDadosTest.kt` | 31 | Parser de CSV de fluxo: formatos de data/valor, tipos aceitos, cabeçalho, aspas, linhas malformadas, ano com 2 dígitos, milhar sem vírgula |
| `ExportarDadosTest.kt` | 24 | Geração do CSV de extrato, rótulos por tipo de categoria, CSV Injection, formatação de moeda/data, casos extremos |
| `ModelsTest.kt` | 11 | Propriedades calculadas (`valorEmAberto`, `quitada`, `podeEditarOuExcluir`), defaults dos modelos |
| `FormatacaoUtilsTest.kt` | 66 | Calculadora (parser de expressão aritmética), `parsearValorMonetario`, `formatarMoeda`/`formatarData`, conversão de fuso do seletor de data |
| `IntegracaoFluxoTest.kt` | 5 | Fluxo ponta a ponta: CSV → Movimentação → totais exibidos na tela |
| `FalhasTest.kt` | 65 | Entradas adversariais, limites, duplicatas, validações — a bateria "o que acontece quando dá errado" |
| **Total** | **250** | |

## Arquivos de lógica pura — status

| Arquivo | Status |
|---|---|
| `ParcelaUtils.kt` | Testado exaustivamente. 6 bugs encontrados e corrigidos aqui. |
| `ImportarDados.kt` | Testado exaustivamente. 2 bugs encontrados e corrigidos (ano de 2 dígitos, ponto de milhar). |
| `ExportarDados.kt` | Testado exaustivamente. 3 bugs encontrados e corrigidos (CSV Injection x2, descrição de baixa dividida). |
| `FormatacaoUtils.kt` | Testado exaustivamente. Criado nessa sequência pra abrigar lógica extraída de telas (calculadora, quantidade, valor digitado, fuso do seletor de data). 1 bug generalizado corrigido (valor digitado com milhar). |
| `Models.kt` | Testado. Sem bug — só documentação de comportamento (`podeEditarOuExcluir` sempre falso em fluxo). |
| `FirebaseRepository.kt` | **Revisado manualmente, sem teste automatizado** (depende do SDK do Firestore, fora do alcance do ambiente). 3 bugs encontrados e corrigidos: escritas travando offline, exclusão sem cascata (cliente/categoria), escutas que morriam pra sempre após um erro. Protegido por `scripts/verificar_padroes_offline.py`. |
| `AppViewModel.kt` | **Revisado manualmente, sem teste automatizado** (Android/Firebase). 1 bug corrigido (cartão excluído bagunçando vencimentos ao editar data). |
| `Versao.kt` / `AtualizacaoDownload.kt` | Revisado. Sem bug — já bem protegido contra rede/JSON malformado. |

## Telas (Compose) — revisadas manualmente, sem JUnit possível

| Tela | Status |
|---|---|
| `ClientesScreen.kt` | Revisada. Busca adicionada. |
| `CategoriasScreen.kt` | Revisada. Tipo travado após criar (correto por design). |
| `ClienteFormScreen.kt` | Revisada. Bug de UX corrigido (sem seta de voltar). |
| `EntradaScreen.kt` | Revisada extensivamente. Vários bugs corrigidos (valor digitado, limite de parcelas, dia de vencimento, edição de fluxo). |
| `SaidaScreen.kt` | Revisada extensivamente. Bug corrigido (percentual sem limite 0-100). |
| `MovimentacoesScreen.kt` | Revisada extensivamente (fluxo de caixa, importação CSV). |
| `MovimentacaoDetalheScreen.kt` | Revisada. Bugs de UI corrigidos (fluxo travando botões). |
| `BaixaDetalheScreen.kt` | Revisada. |
| `ListasScreen.kt` / `ListaDetalheScreen.kt` | Revisadas extensivamente (offline, quantidade, unidades). |
| `ConfiguracoesCartoesScreen.kt` | Revisada. Já valida `melhorDia`/`diaVencimento` corretamente. |
| `ConfiguracoesContaScreen.kt` | Revisada. Já distingue sucesso/erro corretamente. |
| `ConfiguracoesPreferenciasScreen.kt` | Revisada. Lida bem com cartão excluído. |
| `ConfiguracoesScreen.kt` | Revisada (menu simples). |
| `LoginScreen.kt` | Revisada. Bug de UX corrigido (mensagem de sucesso em vermelho). |
| `SeletorDataHora.kt` | Revisada a fundo (bug clássico de fuso horário — já estava certo, agora testado). |
| `VisaoGeralScreen.kt` | Revisada. |
| `MiniCalculadora.kt` (componente) | Lógica extraída e testada exaustivamente. |
| `IndicadorSaldo.kt` (componente) | Revisado (visual, sem lógica). |
| `MainActivity.kt` (navegação) | Revisada a fundo — roteamento exaustivo confirmado pelo compilador. |

## Resumo de bugs corrigidos nessa sequência: **17**

Financeiros/dados: parcela pagando mais que o valor (seleção duplicada), data de vencimento errada (`diaVencimento` ≤ 0), descrição de baixa mostrando valor errado quando dividida, ano de 2 dígitos virando ano 26 d.C., ponto de milhar interpretado errado (valor digitado E no CSV), número de parcelas sem limite, exclusão sem cascata, escuta em tempo real morrendo após erro, cartão excluído bagunçando vencimento, `selecionarMaisAntigasAte` selecionando parcela já quitada.

Segurança: CSV Injection (2 pontos).

UX: sem seta de voltar no formulário de cliente, mensagem de sucesso em vermelho no login, percentual de baixa sem limite, **valores manuais de parcela apagados sem necessidade ao só ajustar data/hora ou dia de vencimento** (`EntradaScreen.kt`).
