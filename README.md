# Ninho Android

Repositório independente preparado para desenvolver o Ninho em Kotlin e Jetpack Compose. O aplicativo contém somente a tela **Ambiente preparado**. Estudos, materiais, revisões, sons, sincronização e IA ainda não foram portados para Android.

Nenhum commit ou staging foi feito durante a preparação. Branches, remotos e publicação ficam sob seu controle; os fontes podem ser adicionados posteriormente com `git add .`.

## Abrir e compilar no Windows

Abra esta pasta no Android Studio. O SDK já foi configurado neste computador pelo arquivo local `local.properties`, que é ignorado pelo Git.

No PowerShell, dentro desta pasta:

```powershell
.\gradlew.bat --version
.\gradlew.bat :app:assembleDebug :app:lintDebug
```

O APK de desenvolvimento será gerado em `app\build\outputs\apk\debug\app-debug.apk`. Os relatórios de lint ficam em `app\build\reports\`. Esses arquivos são locais e ignorados pelo Git. O APK usa a chave padrão de depuração do SDK; não é um pacote de produção assinado para publicação.

Em outro computador, instale o Android SDK com plataforma Android 36 e Build Tools 36.0.0. Copie `local.properties.example` para `local.properties` e ajuste `sdk.dir`. Use um JDK compatível com o Gradle; a preparação deste computador utiliza o Java 25 já instalado. O wrapper baixa e valida a distribuição do Gradle automaticamente na primeira execução. Não é necessário instalar o Gradle globalmente.

Se `JAVA_HOME` apontar para um Java incompatível, selecione o JBR do Android Studio nas configurações de Gradle do IDE ou ajuste `JAVA_HOME` apenas no terminal utilizado para compilar. Nenhuma variável global foi alterada na preparação.

## Versões fixadas

| Componente | Versão |
| --- | --- |
| Android Gradle Plugin | 9.3.1 |
| Gradle Wrapper | 9.7.1, distribuição com SHA-256 fixo |
| Kotlin e plugin Compose | 2.4.20; Kotlin integrado ao AGP |
| Compose BOM | 2025.08.00 |
| Activity Compose | 1.11.0 |
| Compile / Target SDK | 36 / 36 |
| Build Tools | 36.0.0 |
| Android mínimo | 10, API 29 |
| Bytecode Kotlin / Java | JVM 17 |

Gradle e Kotlin foram atualizados para corrigir avisos de segurança. O AGP 9.3.1 está na faixa de suporte declarada do Kotlin 2.4.20; o build foi validado com o patch Gradle 9.7.1. O compilador Kotlin executa no processo do Gradle, evitando uma segunda instância de daemon neste ambiente. [Compatibilidade Kotlin](https://kotlinlang.org/docs/gradle-configure-project.html), [Kotlin integrado ao AGP](https://developer.android.com/build/releases/agp-9-0-0-release-notes#upgrade-to-a-higher-kgp-version), [Compose BOM](https://developer.android.com/develop/ui/compose/bom).

As versões transitivas estão registradas em `buildscript-gradle.lockfile` e `app/gradle.lockfile`. Os checksums ficam em `gradle/verification-metadata.xml`. Esses três arquivos devem ser versionados. O Gradle rejeita dependências alteradas ou ausentes da verificação; não desabilite a proteção para resolver um download divergente. Atualizações dos pacotes precisam incluir revisão dos novos checksums e dos lockfiles.

## Dispositivos e escopo futuro

A base usa APIs Android e Compose, sem código C/C++ próprio nem filtro de ABI que limite um único processador. O desenvolvimento tem como referência celulares ARM64 de alto desempenho e pode usar emuladores x86_64. Nenhum modelo de celular, emulador ou imagem de sistema adicional foi instalado. Equivalência de desempenho e de recursos com o iPhone 15 Pro Max precisa ser avaliada quando houver funcionalidades e um Android físico para testar.

IA local exigirá um motor e um modelo próprios para Android, com análise de memória e desempenho no aparelho. A integração Apple Foundation Models do projeto iOS não é portável diretamente. Esta base não baixa modelos nem solicita acesso à internet ou aos seus documentos.

## Git e dados locais

O `.gitignore` exclui caches, outputs, configurações do computador, IDE, APKs, backups, PDFs pessoais, `.env` e chaves de assinatura. O wrapper Gradle, incluindo seu JAR verificado, faz parte dos fontes e deve ser versionado.

Arquivos de estudo devem permanecer fora do repositório. As regras de ignore não detectam dados pessoais colados dentro de código ou documentação; revise a lista de arquivos antes do seu commit.

## Validação

Consulte `VALIDACAO.md` para os comandos executados e seus limites. Compilação e lint verificam o ambiente inicial; não equivalem a testes funcionais no telefone. Os testes de comportamento serão adicionados junto das funcionalidades futuras.

Consulte [SECURITY.md](SECURITY.md) para a auditoria das dependências, os comandos de verificação e os testes do pacote de produção. O Dependabot está preparado para propor atualizações semanais do Gradle no GitHub, sem fusão automática; nenhuma configuração remota foi ativada nesta alteração.
