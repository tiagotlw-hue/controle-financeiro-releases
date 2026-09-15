# Altera a codificação do terminal para UTF-8 nativo
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::InputEncoding = [System.Text.Encoding]::UTF8

# Configurações do Projeto
$REPO_DIR = "C:\Users\Pessoal\AndroidStudioProjects\ControleFinanceiro"
$BUILD_GRADLE = "$REPO_DIR\app\build.gradle.kts"
$VERSAO_KT = "$REPO_DIR\app\src\main\java\com\controlefinanceiro\app\data\Versao.kt"
$VERSAO_JSON = "$REPO_DIR\versao.json"

Write-Host "`n================================================================" -ForegroundColor Cyan
Write-Host "  Atualizacao de Versao Android (Baseado no JSON)" -ForegroundColor Cyan
Write-Host "================================================================`n"

# Acessa o diretório
Set-Location -Path $REPO_DIR -ErrorAction Stop

# Descobre o nome real da branch atual automaticamente
$BRANCH = (git branch --show-current).Trim()
if ([string]::IsNullOrEmpty($BRANCH)) { $BRANCH = "main" }

# Lendo a versão atual a partir do arquivo JSON
Write-Host "`n=== Lendo a versao atual do JSON ===" -ForegroundColor Green
if (-not (Test-Path $VERSAO_JSON)) {
    Write-Host "[ERRO] Arquivo $VERSAO_JSON nao foi encontrado!" -ForegroundColor Red
    Read-Host "Pressione Enter para sair..."
    exit
}

$jsonOriginal = Get-Content -Raw -Path $VERSAO_JSON | ConvertFrom-Json

# Identifica os campos
$VERSION_CODE_ATUAL = [int]$jsonOriginal.codigo
$VERSION_NAME_ATUAL = if ($jsonOriginal.nome) { $jsonOriginal.nome } else { $jsonOriginal.nomeVersao }

if (-not $VERSION_CODE_ATUAL -or -not $VERSION_NAME_ATUAL) {
    Write-Host "[ERRO] Nao foi possivel ler os campos 'codigo' e 'nome' do seu versao.json" -ForegroundColor Red
    Read-Host "Pressione Enter para sair..."
    exit
}

# Separa as partes da versão (Ex: 0.0.4 -> X.Y.Z)
$partes = $VERSION_NAME_ATUAL.Split('.')
if ($partes.Count -ne 3) {
    Write-Host "[ERRO] O formato do nome da versao no JSON deve ser X.Y.Z (Ex: 0.0.4)" -ForegroundColor Red
    Read-Host "Pressione Enter para sair..."
    exit
}

$maior = [int]$partes[0]
$menor = [int]$partes[1]
$correcao = [int]$partes[2]

# Aplica a regra de limites (Correção até 99, Menor até 9)
$correcao += 1
if ($correcao -gt 99) {
    $correcao = 0
    $menor += 1
    if ($menor -gt 9) {
        $menor = 0
        $maior += 1
    }
}

$VERSION_CODE_NOVO = $VERSION_CODE_ATUAL + 1
$SUGESTAO_NOME = "$maior.$menor.$correcao"

Write-Host "  Versao Atual no JSON: Codigo $VERSION_CODE_ATUAL / Nome $VERSION_NAME_ATUAL" -ForegroundColor Cyan
Write-Host "  Sugestao Proxima Versao: Codigo $VERSION_CODE_NOVO / Nome $SUGESTAO_NOME" -ForegroundColor Yellow

# Solicita alteração ou aceita o padrão
$inputNome = Read-Host "`nDigite a nova versao [Pressione ENTER para aceitar $SUGESTAO_NOME]"
if ([string]::IsNullOrWhiteSpace($inputNome)) {
    $VERSION_NAME_NOVO = $SUGESTAO_NOME
} else {
    $VERSION_NAME_NOVO = $inputNome
}

Write-Host "`n  Versao definida para gravacao: Codigo $VERSION_CODE_NOVO / Nome $VERSION_NAME_NOVO`n" -ForegroundColor Green
$confirmar = Read-Host "Confirma a gravacao dos arquivos e o envio para o GitHub? (s/n)"
if ($confirmar -ne 's') { Write-Host "Cancelado."; Read-Host "Pressione Enter para sair..."; exit }

# 1. Atualizando versao.json
Write-Host "`n=== Atualizando versao.json ===" -ForegroundColor Green
if ($jsonOriginal.nome) { $jsonOriginal.nome = $VERSION_NAME_NOVO } else { $jsonOriginal.nomeVersao = $VERSION_NAME_NOVO }
$jsonOriginal.codigo = $VERSION_CODE_NOVO
$novoJsonText = $jsonOriginal | ConvertTo-Json -Depth 5
[IO.File]::WriteAllText($VERSAO_JSON, $novoJsonText, (New-Object Text.UTF8Encoding($false)))

# 2. Atualizando build.gradle.kts
Write-Host "=== Atualizando build.gradle.kts ===" -ForegroundColor Green
$conteudoGradle = Get-Content -Raw -Path $BUILD_GRADLE
$conteudoGradle = $conteudoGradle -replace 'versionCode\s*=\s*\d+', "versionCode = $VERSION_CODE_NOVO"
$conteudoGradle = $conteudoGradle -replace 'versionName\s*=\s*"[^"]+"', "versionName = `"$VERSION_NAME_NOVO`""
[IO.File]::WriteAllText($BUILD_GRADLE, $conteudoGradle, (New-Object Text.UTF8Encoding($false)))

# 3. Atualizando Versao.kt
Write-Host "=== Atualizando Versao.kt ===" -ForegroundColor Green
$conteudoKt = Get-Content -Raw -Path $VERSAO_KT
$conteudoKt = $conteudoKt -replace 'CODIGO\s*=\s*\d+', "CODIGO = $VERSION_CODE_NOVO"
$conteudoKt = $conteudoKt -replace 'NOME\s*=\s*"[^"]+"', "NOME = `"$VERSION_NAME_NOVO`""
[IO.File]::WriteAllText($VERSAO_KT, $conteudoKt, (New-Object Text.UTF8Encoding($false)))

# Git Etapa 1: Salva as alterações locais
Write-Host "`n=== Preparando alterações locais (git commit) ===" -ForegroundColor Green
git add -A
git commit -m "Versao $VERSION_NAME_NOVO (build $VERSION_CODE_NOVO)"
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERRO] O commit falhou. Configure seu user.name e user.email do Git se ainda nao fez." -ForegroundColor Red
    Read-Host "Pressione Enter para sair..."
    exit
}

# Git Etapa 2: Busca novidades do GitHub e junta com o seu commit de forma limpa
Write-Host "`n=== Sincronizando com o GitHub (git pull --rebase) ===" -ForegroundColor Green
git pull origin "$BRANCH" --rebase
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERRO] Falha ao sincronizar (pull). Se houver conflitos reais de codigo, resolva-os no Android Studio." -ForegroundColor Red
    Read-Host "Pressione Enter para sair..."
    exit
}

# Git Etapa 3: Envia tudo junto para o servidor
Write-Host "`n=== Enviando para o GitHub (git push) ===" -ForegroundColor Green
git push origin "$BRANCH"
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERRO] O push falhou. Verifique suas permissoes ou se o navegador pede autenticacao." -ForegroundColor Red
    Read-Host "Pressione Enter para sair..."
    exit
}

Write-Host "`n================================================================" -ForegroundColor Green
Write-Host "  Pronto! Versao $VERSION_NAME_NOVO enviada com sucesso para a branch $BRANCH.  " -ForegroundColor Green
Write-Host "================================================================`n"

Read-Host "Pressione Enter para fechar..."
