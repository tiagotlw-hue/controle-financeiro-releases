#!/usr/bin/env python3
"""
Verificações estáticas do comportamento offline do app.

Por quê um script em vez de um teste JUnit? As duas garantias abaixo vivem no
FirebaseRepository/AppViewModel, que dependem do SDK do Firestore de verdade — não dá
pra compilar isolado sem o projeto Android completo (Gradle + Google Maven), então não
tem como escrever um teste JUnit de unidade pra elas hoje. Em vez de deixar sem
cobertura nenhuma, este script varre o CÓDIGO-FONTE procurando os padrões exatos que
já causaram bug em produção uma vez — e barra o build se alguém reintroduzir um deles
sem perceber.

Uso: python3 verificar_padroes_offline.py
Sai com código 0 se tudo estiver certo, 1 se achar algum problema (dá pra plugar
como um step a mais no mesmo workflow do build-apk.yml).
"""
import re
import sys

problemas = []

# ---------------------------------------------------------------------------
# Regra 1: nenhuma escrita no Firestore (set/update/add/commit) pode ter um
# .await() logo em seguida. Esse era exatamente o bug original: .await() só
# resolve quando o SERVIDOR confirma o recebimento, o que nunca acontece
# offline — a tela ficava travada esperando pra sempre. Escritas devem ser
# "fire and forget": o Firestore já aplica no cache local na hora e sincroniza
# sozinho depois.
# ---------------------------------------------------------------------------
def checar_writes_sem_await(caminho):
    with open(caminho, encoding="utf-8") as f:
        linhas = f.readlines()

    # Pega o corpo de cada função "suspend fun" pra não confundir com os poucos
    # `.get(Source.SERVER).await()` de leitura, que são esperados e corretos.
    dentro_de_bloco_suspeito = False
    for i, linha in enumerate(linhas, start=1):
        # write imediatamente seguida de .await() na MESMA linha (o caso mais comum)
        m = re.search(r'\.(set|update|delete)\([^)]*\)\.await\(\)', linha)
        if m:
            problemas.append(
                f"{caminho}:{i}: escrita (.{m.group(1)}) com .await() encadeado — "
                f"vai travar a tela offline. Trecho: {linha.strip()}"
            )
        if re.search(r'batch\.commit\(\)\.await\(\)', linha):
            problemas.append(
                f"{caminho}:{i}: batch.commit().await() — vai travar a tela offline "
                f"(um commit de lote tem a mesma trava que um write comum). Trecho: {linha.strip()}"
            )
        # write que quebra em duas linhas, com o .await() sozinho na linha seguinte
        if re.search(r'\.(set|update|delete)\(\s*$', linha) or linha.strip() == "batch.commit()":
            dentro_de_bloco_suspeito = True
            continue
        if dentro_de_bloco_suspeito:
            if linha.strip().startswith(".await()"):
                problemas.append(
                    f"{caminho}:{i}: .await() logo após uma escrita multi-linha — "
                    f"vai travar a tela offline."
                )
            dentro_de_bloco_suspeito = False


# ---------------------------------------------------------------------------
# Regra 2: toda função "iniciarEscutaX" que usa o padrão de trava
# "if (jobX != null) return" (escuta iniciada uma vez só, não reiniciada a
# cada troca de tela) PRECISA resetar jobX = null quando a coleta terminar —
# senão, se o listener cair por qualquer erro (mesmo um só, passageiro), a
# trava impede pra sempre qualquer nova tentativa de reconectar, e aquela
# lista fica congelada pelo resto da sessão do app.
# ---------------------------------------------------------------------------
def checar_reset_de_job_apos_erro(caminho):
    with open(caminho, encoding="utf-8") as f:
        conteudo = f.read()

    # Acha cada "fun nomeQualquer() { ... }" que contenha o padrão de trava
    # "if (jobX != null) return", e confere se o MESMO bloco de função também
    # tem "jobX = null" em algum lugar (idealmente dentro de um finally).
    for m in re.finditer(r'fun (\w+)\(\)\s*\{', conteudo):
        nome_funcao = m.group(1)
        inicio = m.end()
        # pega o corpo da função contando chaves (aninhamento simples, suficiente aqui)
        profundidade = 1
        pos = inicio
        while profundidade > 0 and pos < len(conteudo):
            if conteudo[pos] == '{':
                profundidade += 1
            elif conteudo[pos] == '}':
                profundidade -= 1
            pos += 1
        corpo = conteudo[inicio:pos]

        m_guard = re.search(r'if\s*\(\s*(job\w+)\s*!=\s*null\s*\)\s*return', corpo)
        if not m_guard:
            continue  # essa função não usa o padrão "trava" — regra não se aplica
        job_var = m_guard.group(1)

        if not re.search(re.escape(job_var) + r'\s*=\s*null', corpo[m_guard.end():]):
            problemas.append(
                f"{caminho}: fun {nome_funcao}() usa \"if ({job_var} != null) return\" "
                f"mas nunca reseta \"{job_var} = null\" depois — se o listener cair por "
                f"erro, essa escuta fica travada pro resto da sessão do app, sem jeito "
                f"de reconectar."
            )


checar_writes_sem_await("app/data/FirebaseRepository.kt")
checar_reset_de_job_apos_erro("app/viewmodel/AppViewModel.kt")

if problemas:
    print(f"❌ {len(problemas)} problema(s) de comportamento offline encontrado(s):\n")
    for p in problemas:
        print(f"  - {p}")
    sys.exit(1)
else:
    print("✅ Nenhum padrão que quebra o comportamento offline foi encontrado.")
    sys.exit(0)
