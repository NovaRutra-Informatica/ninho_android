# Validação do ambiente Android

## Atualização de segurança — 15/09/2026

Ambiente: Windows 11, JDK 25, Gradle 9.7.1, AGP 9.3.1, Kotlin/Compose compiler 2.4.20, SDK 36 e Build Tools 36.0.0. Detalhes das correções e limites em [SECURITY.md](SECURITY.md).

- Auditoria final: **339 combinações Maven de pacote/versão, 54 configurações, zero erros de resolução e zero avisos OSV**. Controle positivo de pacote vulnerável reconhecido. Os dois avisos oficiais do Gradle foram conferidos separadamente; a versão 9.7.1 está corrigida para ambos.
- Build debug e release, lint debug e release, com locks e checksums aplicados, sem `--write-*`: **BUILD SUCCESSFUL**, 95 tarefas executadas em 1m06s. A execução usou o cache de downloads local, em modo offline, mantendo verificação estrita. Log em `build/security/verified-build.log`.
- Lint debug/release: **zero erros, três avisos** de versão: compile/target SDK 37 disponível e Activity Compose 1.13.0 disponível. Nenhum aviso foi suprimido; avisos de versão não foram apresentados como vulnerabilidades.
- **8 testes Python do auditor passaram**, incluindo resposta incompleta, controle positivo ausente, paginação, erro de resolução e Gradle antigo não indexado no OSV.
- **19 verificações de segurança do APK passaram**, no PowerShell 7 e no Windows PowerShell 5.1. O teste aceitou o APK release real, rejeitou o APK debug real, rejeitou manifesto com permissões/componentes indevidos, backup/transferência inseguros, DTD e APK inválido. As mutações de manifesto usam fixtures isoladas, sem alterar o APK.
- O teste de integridade aceitou um artefato Maven com checksum correto e o Gradle recusou o checksum adulterado, em um projeto temporário. Executado no PowerShell 7 e no Windows PowerShell 5.1; evidências em `build/security/integrity-*`.
- O wrapper JAR 9.7.1 foi conferido: SHA-256 `7a9ce74cff467ca1bf60a4fcd9f05185acceda4d0f382434d393e17864262c5d`. A distribuição exige SHA-256 `acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a`.
- Foram preservados os repositórios independentes e o Git sob controle do usuário. Nenhum staging, commit, push ou instalação foi realizado nesta revisão.

Artefatos após a correção:

| Artefato local | Bytes | SHA-256 |
| --- | ---: | --- |
| `app/build/outputs/apk/debug/app-debug.apk` | 27.574.255 | `cf795c76aef15ab29b93d6e344c0b383285d96b5e34c15b0343c77ed136d5f6a` |
| `app/build/outputs/apk/release/app-release-unsigned.apk` | 20.223.258 | `2f68f0c993d4e46840e0f363807fcf649a7cc49ea8d9355fa6274955ac3f50ac` |

O release continua sem assinatura de distribuição. Não houve execução em telefone/emulador nem teste instrumentado do UTP. O scanner não audita o JDK/SDK globais ou todas as bibliotecas sombreadas dentro dos binários das ferramentas. Esses resultados não equivalem a uma garantia de ausência de toda vulnerabilidade.

## Registro histórico — preparação de 14/09/2026

Os resultados abaixo descrevem a preparação anterior à atualização de segurança, não as versões atuais.

Data: 14/09/2026. Preparação feita no Windows 11, com JDK 25, Gradle 9.1.0, Android SDK 36 e Build Tools 36.0.0 já disponíveis ou obtidos pelo wrapper.

## Verificações concluídas

- `gradlew.bat --version`: execução do Gradle 9.1.0 em Java 25 confirmada.
- `gradlew.bat :app:assembleDebug :app:lintDebug --console=plain`: **BUILD SUCCESSFUL**, 46 tarefas, execução final em 13 segundos após o download inicial das dependências.
- Lint: **0 erros e 3 avisos**, todos sobre versões mais novas disponíveis (Gradle, Compose BOM e Activity Compose). As versões compatíveis e fixadas foram preservadas; nenhum aviso foi suprimido. Relatórios locais em `app/build/reports/lint-results-debug.html` e `.txt`.
- `apksigner verify --verbose`: assinatura de depuração válida, esquema APK v2.
- `aapt dump badging`: pacote `app.ninho.android`, versão `0.1.0`, Android mínimo API 29, target API 36, nome `Ninho`, suporte no APK a `arm64-v8a`, `armeabi-v7a`, `x86` e `x86_64`.
- Git: branch `main`, sem remoto, sem commit e sem arquivos no índice.
- `.gitignore`: `local.properties`, caches Gradle, outputs/APK, `.env`, configuração de assinatura e PDFs pessoais confirmados como ignorados. Wrapper JAR e exemplo de configuração confirmados como versionáveis.

O JAR do wrapper foi obtido do repositório oficial Gradle, tag `v9.1.0`, e conferido contra o checksum publicado no serviço oficial de distribuições:

```text
gradle-wrapper.jar SHA-256
76805e32c009c0cf0dd5d206bddc9fb22ea42e84db904b764f3047de095493f3

gradle-9.1.0-bin.zip SHA-256
a17ddd85a26b6a7f5ddb71ff8b05fc5104c0202c6e64782429790c933686c806
```

## Artefato local

```text
app/build/outputs/apk/debug/app-debug.apk
Tamanho: 27.672.199 bytes
SHA-256: 67298dc5dd8f9e8c80933204e1e40f826bbb50698964dfd02040c87611ed215d
```

O APK e a chave debug não são versionados. Nenhuma chave de produção foi criada.

## Limites

Não foram executados testes em Android físico ou emulador, nem uma instalação do APK. A validação é do ambiente e da compilação da tela inicial; ela não comprova funcionalidades de estudo ou IA, que ainda não foram implementadas nesta base.

O `apksigner` do SDK mostrou um aviso de acesso nativo do Java 25, mas verificou a assinatura com sucesso. Nenhuma configuração global foi alterada para suprimir esse aviso.
