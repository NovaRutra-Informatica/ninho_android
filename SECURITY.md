# Segurança do Ninho Android

Revisão em 15/09/2026. O projeto ainda contém somente a tela inicial de preparação, sem contas, IA, documentos ou comunicação de rede.

## Dependências corrigidas

A consulta inicial ao OSV encontrou **21 combinações de pacote/versão com 49 avisos distintos**, em dependências de compilação, lint e testes. Nenhuma dessas correspondências estava nas configurações de runtime debug ou release do aplicativo. Além delas, o Gradle 9.1.0 era afetado por dois avisos publicados diretamente pelo projeto Gradle.

| Componente | Versão anterior | Versão usada após a correção |
| --- | --- | --- |
| Gradle | 9.1.0 | 9.7.1 |
| Android Gradle Plugin | 9.0.1 | 9.3.1 |
| Kotlin / compilador Compose | 2.2.10 | 2.4.20 |
| Netty das ferramentas | 4.1.93 / 4.1.110 | 4.1.138.Final |
| Bouncy Castle | 1.79 | 1.84 |
| Commons Lang | 3.16.0 | 3.18.0 |
| Apache HttpClient | 4.5.6 | 4.5.14 |
| jose4j | 0.9.5 | 0.9.6 |
| JDOM | 2.0.6 | 2.0.6.1 |
| Protobuf das ferramentas | incluía 3.24.4 | 3.25.5 e 4.28.3, conforme a ferramenta |

Os avisos do Gradle são [CVE-2026-22865](https://github.com/gradle/gradle/security/advisories/GHSA-mqwm-5m85-gmcv) e [CVE-2026-22816](https://github.com/gradle/gradle/security/advisories/GHSA-w78c-w6vf-rw82), relativos à busca de artefatos em outro repositório após falha de conexão. Kotlin 2.4.20 cobre a desserialização insegura de metadados do build cache descrita em [CVE-2026-53914](https://github.com/advisories/GHSA-r937-wjx7-w2jp). As referências completas detectadas antes da correção estão em `security/dependency-audit.json`.

As constraints do classpath e os BOMs das ferramentas estabelecem versões mínimas, sem forçar downgrade de versões futuras. As dependências de segurança do lint/UTP não são adicionadas ao runtime do aplicativo. A versão AGP 9.3.1 foi escolhida dentro da faixa declarada de compatibilidade do Kotlin 2.4.20; não foi feita uma migração para releases de prévia.

A nova consulta examinou **339 combinações de pacote/versão em 54 configurações resolvíveis, sem erros de resolução, e retornou zero avisos conhecidos**. O inventário cobre as dependências Maven transitivas do projeto e do classpath dos plugins. O script consulta todas as páginas da API, falha quando a resposta está incompleta e exige que um pacote antigo com vulnerabilidade conhecida seja reconhecido como controle positivo.

O OSV não associou os avisos recentes do Gradle ao coordenado Maven consultado; por isso o scanner também confere explicitamente as faixas dos dois avisos oficiais do Gradle. Essa checagem adicional cobre esses avisos específicos, sem prometer detectar todo aviso futuro da distribuição.

## Integridade e configuração do aplicativo

- O wrapper JAR e a distribuição Gradle 9.7.1 foram conferidos contra os SHA-256 oficiais. A distribuição exige checksum no wrapper.
- O Gradle usa dependency locking estrito no projeto e no classpath do buildscript. Os artefatos e metadados Maven usam verificação SHA-256.
- Os checksums iniciais foram gerados a partir dos artefatos obtidos dos repositórios HTTPS configurados, após a auditoria de versões. Eles detectam alterações posteriores; não constituem uma auditoria independente do código de cada artefato nem verificação de assinaturas dos autores.
- A configuração mantém backup desativado, regras que excluem dados de backup/transferência e bloqueio explícito de tráfego sem TLS. O APK não solicita acesso à internet.
- O verificador do APK examina o manifesto compilado, rejeitando depuração e permissões/componentes exportados fora da lista esperada. O launcher e o receiver de instalação de perfis do AndroidX têm regras específicas.

## Repetir as verificações

Use Python 3 e o JDK/SDK configurados para o projeto, sem instalar dependências Python adicionais:

```powershell
.\gradlew.bat -I scripts/security-inventory.init.gradle securityInventory --no-configuration-cache
python scripts/audit_dependencies.py
python -m unittest discover -s scripts -p 'test_*.py' -v
.\gradlew.bat :app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease
powershell -ExecutionPolicy Bypass -File scripts/test-dependency-integrity.ps1
.\scripts\test-verify-apk-security.ps1 -SdkPath "$env:LOCALAPPDATA\Android\Sdk" -JavaHome "C:\Program Files\Java\jdk-25"
```

O scanner retorna código 1 quando encontra avisos e código 2 quando não consegue concluir a consulta. O relatório completo fica em `build/security/osv-report.json`; os logs e relatórios de teste são ignorados pelo Git. O teste de integridade usa um projeto temporário em `build/security`, aceita um artefato com checksum correto e comprova que o Gradle bloqueia o mesmo artefato quando o checksum esperado é adulterado.

Os caminhos do SDK/JDK no último comando correspondem a este computador; ajuste os parâmetros em outra instalação. O verificador invoca a CLI Java do SDK diretamente porque o launcher `apkanalyzer.bat` instalado interpreta incorretamente a versão Java 25. Nenhuma variável global é alterada.

Ao atualizar versões, revise os avisos e a origem dos artefatos antes de regenerar os locks/checksums:

```powershell
.\gradlew.bat -I scripts/security-inventory.init.gradle securityInventory :app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease --write-locks --write-verification-metadata sha256 --no-configuration-cache
```

Depois, execute novamente sem os argumentos `--write-*`, examine o diff e repita a auditoria. A verificação atual cobre os artefatos necessários no Windows; outro sistema operacional pode precisar de checksums adicionais dos artefatos específicos de sua plataforma. Não aceite automaticamente uma mudança de checksum de um artefato já existente.

## Limites

Não foram auditados o JDK/SDK instalados globalmente, o sistema operacional, todas as bibliotecas embutidas ou sombreadas dentro dos binários das ferramentas, nem o código de cada dependência. A consulta identifica avisos publicados para nomes e versões; não demonstra explorabilidade nem ausência de falhas desconhecidas.

Compilação e lint não executam o UTP em um dispositivo. Não houve teste instrumentado, instalação em telefone ou emulador, assinatura de produção ou publicação. O APK release é um artefato local sem assinatura de distribuição. Os resultados efetivos estão em [VALIDACAO.md](VALIDACAO.md).
