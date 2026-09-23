# Ninho Android

Aplicativo nativo em Kotlin e Jetpack Compose, com estudos e acompanhamento inteiramente offline. A interface mantém a coruja original do Ninho, tons de verde, cartões suaves, navegação animada e temas claro/escuro do sistema.

## O que está implementado

- Hoje com tempo estudado, sessões recentes e cartões proativos da assistente. O cabeçalho mostra apenas o foguinho e o número da sequência ao lado de Perfil: toque para abrir o calendário mensal dos dias estudados. Navegação inferior: Hoje, Estudos, Foco, Revisões e Assistente; foguinho, Perfil e Ajuda têm rótulos de acessibilidade e alvos de pelo menos 48 dp.
- Cadastro de matérias, sessões, duração, conteúdo estudado e autoavaliação.
- Foco na navegação inferior: temporizador com presets de 15/25/45/60 minutos, edição de 1 a 240 minutos e cronômetro livre.
- Relógio monotônico e timestamps persistidos: pausa, tela apagada, recriação do processo e reinício do telefone não dependem de contagem de ticks. Alterar o relógio do sistema durante a mesma inicialização não altera o tempo estudado. O cronômetro livre tem limite de 24 horas por sessão.
- Questões próprias com pergunta, gabarito, resposta escrita e avaliação de acerto. As tentativas e respostas ficam salvas.
- Revisões por regras transparentes: 1/3/7 dias conforme autoavaliação; último erro antecipa a revisão. Essas regras não são apresentadas como geração de IA.
- Análise local pequena **já incluída no APK**, sem importação obrigatória: estatísticas explicáveis se adaptam às sessões, autoavaliações, tentativas e pausas registradas. Não há caixa de chat nem geração de linguagem simulada.
- Aviso silencioso opcional do temporizador via AlarmManager, sem serviço contínuo, áudio, vibração ou audio focus.
- LiteRT-LM opcional em **Assistente → Modelos e importação** e **Perfil → Opções avançadas**, para experimentar geração de texto com um `.litertlm` próprio. A tela identifica a Íris, o motor incluído, o runtime opcional, nome/tamanho do arquivo e estado de compatibilidade.
- Três widgets nativos para a tela inicial e atalhos Foco/Revisões ao manter pressionado o ícone do aplicativo.

Os dados são armazenados no diretório privado do aplicativo com `AtomicFile`; uma sessão e o reset do timer são salvos juntos para evitar duplicação. Leitura, gravação, importação e inferência executam fora da thread da interface. A UI só atualiza o relógio enquanto a tela está visível, e o motor de IA é liberado após cada solicitação. Nenhum modelo é carregado ao abrir o aplicativo.

## Perfil local, boas-vindas e tutoriais

Na primeira abertura, o Ninho apresenta **dez telas de perguntas**, sem barra de navegação nem acesso antecipado ao Início: nome; objetivo; motivação/prazo; matérias; ponto de partida; rotina; dias/horário; tempo/blocos; preferências/dificuldades; conforto e resumo. A coruja animada conduz cada etapa com uma fala própria. As transições acompanham avançar/voltar e podem ser desativadas no próprio cadastro. Campos, botões e títulos usam DM Sans/Fraunces locais, superfícies suaves e o verde do Ninho. Nome e objetivo são necessários; as outras respostas são opcionais. Tudo pode ser editado em **Perfil**, no cabeçalho, com salvamento automático. As respostas e o passo atual ficam salvos: fechar e reabrir retoma a pergunta em andamento. O passo é guardado separadamente, sem mudar o schema do perfil; a conclusão só é registrada no último botão, após as respostas serem gravadas. Os estados v1/v2 são migrados para v3 sem descartar matérias, sessões, questões ou temporizador existentes.

Ao criar o perfil, o motor incluído prepara um ponto de partida usando as respostas, sem download ou importação. A tela informa que é uma análise estatística local, não um modelo de linguagem. Conforme o usuário registra sessões e questões, novos cartões explicam o que mudou. Perfil e eventual texto gerado pelo modelo opcional são armazenados separadamente: editar objetivos marca esse texto como desatualizado; uma resposta iniciada com outro perfil não sobrescreve o plano novo.

Cada primeira visita a Hoje, Estudos, Foco, Revisões, Meu perfil e Minha assistente mostra uma explicação dos componentes. **Ajuda** repete o tutorial atual; Meu perfil permite reiniciar todos. As conclusões ficam no armazenamento local. Esta versão Android ainda não possui telas próprias de Agenda, Materiais e Progresso; não são apresentados tutoriais de funções inexistentes.

Em Meu perfil, o tema (sistema/claro/escuro) e a opção de reduzir movimento também são salvos automaticamente. O tema escuro usa superfícies neutras, mantendo o verde como cor de destaque. Não há botão Salvar para preferências.

## Backup leve diário e recuperação

Em **Perfil → Seu caminho está guardado**, o Ninho mostra a última cópia e permite **Exportar backup leve**, **Restaurar um backup** e abrir as configurações de **Backup do Android**. Também há uma entrada para restaurar na primeira tela de boas-vindas.

A cópia inclui tudo que a versão Android persiste atualmente: perfil e objetivos, matérias, sessões e notas, questões/gabaritos/respostas, configurações, tutoriais, uso agregado e o plano salvo da assistente. Progresso e sequência são reconstruídos dos registros. Esta versão ainda não armazena cursos, aulas ou materiais como entidades próprias; não promete incluí-los. O backup não percorre diretórios de anexos, modelos, caches ou widgets e não carrega IA.

O arquivo `.ninho-backup.gz` usa gzip, versão e checksum SHA-256. Limites: **2 MiB compactados / 8 MiB expandidos**. Há uma cópia por data local, atualizada após alterações com debounce de 2 s, até **7 dias** guardados internamente e até **3 cópias de segurança anteriores a restaurações manuais**. A gravação é atômica e só ocorre depois que o estado canônico foi salvo; falhar o backup não desfaz nem bloqueia os estudos. Dados idênticos não geram nova compactação. O snapshot do temporizador fica pausado, sem registrar estudo automaticamente.

Um `JobScheduler` persistente solicita execução diária com janela flexível de 6 horas, bateria e armazenamento disponíveis, sem serviço contínuo, rede própria ou IA. O Android pode adiar ou impedir a tarefa. Abrir o aplicativo e salvar estudos também atualiza a cópia; não há promessa de execução em um horário exato ou enquanto o app estiver encerrado à força.

O **Auto Backup/transferência de aparelho do Android** recebe exclusivamente `files/compact-backup/latest.ninho-backup.gz`, até 2 MiB. O banco canônico, rotação interna, preferências do runtime e pesos permanecem fora. O backup na nuvem exige recursos de criptografia do sistema; o envio depende do backup habilitado pelo usuário, da conta e das condições do Android. O Ninho continua sem permissão `INTERNET`. Não ativamos conta/backup na máquina de teste e não afirmamos que o Google recebeu uma cópia.

Ao reinstalar, se o Android entregar um snapshot válido e ainda não houver arquivo canônico, o Ninho valida e recupera os dados **antes do onboarding**. Estado atual existente, mesmo corrompido, nunca é substituído automaticamente. Um snapshot inválido é preservado e cópias internas válidas mais recentes podem ser usadas. Modelos precisam ser importados novamente. Desinstalar apaga também a rotação interna; somente uma cópia exportada para fora do app ou efetivamente guardada pelo sistema permite recuperação depois.

A exportação abre o seletor nativo **Arquivos**: escolha uma pasta sua ou um provedor como Drive, se disponível. O Ninho não faz login nem upload por conta própria. O arquivo é compacto, mas contém os dados pessoais em texto comprimido; guarde-o em um local privado. A importação aceita apenas o formato do Ninho Android, verifica tamanho/versão/checksum/registros e mostra uma confirmação antes de substituir os estudos atuais. Uma cópia local anterior é obrigatória antes dessa substituição. Exportações para outros sistemas não são anunciadas como interoperáveis.

Referências: [Auto Backup e condições de execução](https://developer.android.com/identity/data/autobackup), [seletor de documentos do Android](https://developer.android.com/training/data-storage/shared/documents-files).

## Assistente proativa incluída

O motor `LocalAnalytics` usa acertos suavizados por `(acertos + 2) / (tentativas + 4)` e só indica dificuldade estatística com pelo menos 5 tentativas. Um horário recorrente exige ao menos 6 sessões, 3 dias distintos na mesma faixa de 3 horas e 40% das sessões nessa faixa. Frequência não é produtividade. Avaliações “Preciso retomar”, revisões pendentes, primeiras sessões e pausas explícitas também geram ações explicadas. Uma pausa não é tratada como desatenção. A análise usa até 90 dias para padrões; uma revisão antiga ainda pode permanecer devida. Não existe treino próprio ou LLM oculto anunciado como concluído. Respostas livres permanecem no perfil, mas o motor incluído não promete interpretação semântica geral desses textos.

As sugestões são recalculadas fora da thread da interface, apenas com a janela em primeiro plano, após dados relevantes mudarem (debounce de 750 ms e intervalo mínimo de 5 s), ou ao retornar ao app. Não há worker periódico nem serviço de monitoramento. A rotina e o perfil ficam no armazenamento canônico; o plano estatístico é derivado novamente desses fatos. A inferência opcional é cancelada ao perder o primeiro plano e libera o motor/pesos em `finally`; se a inicialização nativa já começou, a liberação aguarda o runtime devolver o controle.

O registro de uso guarda somente visitas, segundos ativos por tela e pausas explícitas, agregado por data/rota, até 90 dias e 540 linhas. Conta apenas enquanto esta janela está ativa, no máximo 60 s após uma interação; não observa outros apps e nunca soma navegação aos minutos estudados. **Perfil** permite desativar e apagar esse histórico. Como todo registro local por pulsos, uma interrupção abrupta pode perder os últimos segundos ainda não gravados.

## Temporizador em segundo plano

Ative **Perfil → Aviso silencioso do temporizador**. No Android 13+, o sistema pede permissão de notificações somente nessa ação explícita; recusar não impede estudar. Não há pedido de alarme exato. O aviso usa `setAndAllowWhileIdle` e canal silencioso de baixa importância, sem serviço contínuo, media session, wake lock próprio ou audio focus. Não tenta parar música, vídeo/PiP ou widgets de outros apps.

O Android pode adiar esse alarme inexato em Doze/economia de energia; a contagem por timestamps continua correta. Pausar, descartar ou mudar a preferência cancela/reprograma o aviso; após reinício do aparelho, um receiver interno recupera o temporizador salvo. O aviso abre Foco, mas não salva estudo automaticamente. Bloqueios do sistema, encerramento forçado e personalizações do fabricante podem impedir entrega até abrir o app novamente. Referências: [AlarmManager e alarmes inexatos](https://developer.android.com/develop/background-work/services/alarms), [permissão de notificações](https://developer.android.com/develop/ui/views/notifications/notification-permission), [ciclo de vida da Activity](https://developer.android.com/guide/components/activities/activity-lifecycle).

## Sequência, widgets e atalhos do Android

O foguinho com número no cabeçalho, ao lado de **Perfil**, abre o calendário de estudos. Use as setas para percorrer os meses ou **Ir para hoje** para voltar ao mês atual. Dias estudados têm preenchimento verde; hoje tem contorno laranja. O calendário mantém todo o histórico registrado, inclusive quando a sequência recomeça. Não há um card de sequência na Home.

A sequência conta dias consecutivos com **sessão salva de duração positiva ou questão respondida**, mesmo quando a resposta foi marcada como incorreta. Várias sessões no mesmo dia contam uma vez. Abrir telas, navegar e deixar um temporizador sem registrar não contam. Registros futuros são ignorados. O cálculo usa o calendário/fuso atual do celular, preserva a sequência terminada ontem até o fim de hoje e começa de novo após um dia sem registro; mudanças de horário de verão não são tratadas como blocos fixos de 24 horas.

Em **Perfil → Seu ninho na tela inicial**, escolha um widget e confirme no Android. Também é possível manter pressionado um espaço vazio da tela inicial, tocar em **Widgets**, procurar **Ninho** e arrastar:

- **Minha sequência:** foguinho, número de dias e indicação de estudo hoje.
- **Meu dia de estudo:** minutos salvos/meta diária e quantidade de matérias já estudadas com revisão devida.
- **Hora de focar:** duração configurada e atalho para Foco; uma sessão aberta é indicada, mas o widget não apresenta um contador em tempo real nem inicia uma sessão sem escolher a matéria.

Os widgets usam `RemoteViews`, fontes e coruja locais, temas claro/escuro do aparelho e podem ser redimensionados. Mostram contagens; não expõem nome, objetivos, notas ou respostas. Cada toque abre a tela correspondente, respeitando o cadastro inicial. A fixação depende do launcher e só é solicitada após tocar no botão. Mantenha pressionado o ícone do Ninho para os atalhos nativos **Hora de focar** e **Revisões**.

As atualizações partem de gravações reais, abertura/retorno ao aplicativo, mudança de data/fuso/relógio, reinício e atualização do pacote. Um snapshot agregado privado, limitado a 256 KiB e validado, permite atualizar sem carregar o histórico completo ou qualquer IA. O Android recebe um único alarme inexato, sem acordar o aparelho, para a próxima meia-noite enquanto houver widgets, além da atualização de contingência do launcher a cada seis horas. Não há serviço ou loop contínuo. Economia de bateria, encerramento forçado e o launcher podem atrasar a atualização, inclusive minutos/revisões; abrir o Ninho a atualiza novamente. A soma exibida no widget é limitada a 24 horas por dia para rejeitar agregados corrompidos; os registros canônicos não são alterados.

Estas são integrações locais de estudo com o sistema. Nenhuma permissão do Health Connect ou dado de saúde é usado. Referências: [widgets nativos](https://developer.android.com/develop/ui/views/appwidgets), [atualização e limites do Android](https://developer.android.com/develop/ui/views/appwidgets/advanced), [atalhos de aplicativo](https://developer.android.com/develop/ui/compose/system/shortcuts/creating-shortcuts).

## Modelo de linguagem opcional

Em **Assistente → Modelos e importação** (ou **Perfil → Opções avançadas**), a Íris informa que o acompanhamento usa **Ninho LocalAnalytics**, motor estatístico incluído, sem LLM. O motor opcional é **Google LiteRT-LM 0.17.1 em CPU**. Para importar:

1. Obtenha um modelo de geração de texto `.litertlm` compatível com esse runtime/backend Android e confira a licença/instruções do autor.
2. Copie o arquivo para o celular, por exemplo para Downloads.
3. Toque em **Importar modelo opcional** e escolha o arquivo no seletor do Android. O aplicativo faz uma cópia privada, limitada a **3 GiB**, sem baixar pesos ou solicitar internet. Precisa haver espaço para essa cópia.
4. Confira o nome/tamanho/status e toque em **Gerar plano com este modelo**. A primeira execução verifica a compatibilidade; importar não garante que o modelo execute.

`.gguf` do LM Studio, `.task`, `.tflite` avulso e ZIP não são aceitos; renomear a extensão não converte o formato. RAM necessária pode ser maior que o tamanho do arquivo. Prefira arquivos menores/quantizados preparados pelo autor para celular e confira requisitos de memória, backend e versão. **Cancelar**, **Trocar** e **Remover modelo** permanecem disponíveis; remover pesos preserva perfil e estudos.

A implementação usa o runtime oficial [LiteRT-LM para Kotlin](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.17.1/docs/api/kotlin/getting_started.md), com CPU de quatro threads, contexto de até 8.192 tokens e saída de até 512 tokens. O perfil canônico completo é reenviado em toda análise. Apenas a amostra de sessões e tentativas é limitada, removendo objetos completos para preservar JSON válido; o modelo não recebe ferramentas nem executa ações. A orientação generativa é separada das regras locais e informa que pode conter erros. A operação é cancelável; durante a inicialização nativa, o cancelamento é aplicado assim que o runtime devolve o controle.

O modelo deve ser obtido separadamente, respeitando sua licença. Pesos não foram incluídos, baixados ou treinados nesta alteração. Velocidade, memória, qualidade em português e compatibilidade de cada modelo precisam ser medidos em um Android físico de alto desempenho. A integração foi compilada, mas não houve inferência com pesos nem benchmark no telefone.

## Limites atuais

Não há importação de banco de questões em lote, leitura de PDFs, exportação/restauração de backup, edição/exclusão de matérias ou sons ambiente. A prática de questões utiliza autoavaliação com gabarito; não promete correção automática. Desinstalar o app remove seus dados e o modelo importado. Esses recursos não são simulados na interface.

Nenhum commit ou staging foi feito durante a preparação. Branches, remotos e publicação ficam sob seu controle; os fontes podem ser adicionados posteriormente com `git add .`.

## Rodar no emulador do Android Studio (Windows)

Abra esta pasta no Android Studio e inicie seu emulador em **Tools → Device Manager**. Você também pode selecionar o dispositivo na barra superior e usar **Run 'app'** (Shift + F10).

Para rodar pelo terminal **PowerShell** do Android Studio, na raiz deste projeto:

```powershell
Set-Location 'C:\Users\aless\StudioProjects\Ninho-Android'
$ninhoAdb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
& $ninhoAdb devices -l
.\gradlew.bat :app:assembleDebug
& $ninhoAdb -s emulator-5554 install -r '.\app\build\outputs\apk\debug\app-debug.apk'
& $ninhoAdb -s emulator-5554 shell am start -n 'app.ninho.android/.MainActivity'
```

O emulador conectado neste computador foi identificado como `emulator-5554`. A atualização de 23/09/2026 via `install -r` abriu corretamente e migrou v2→v3 preservando o perfil e timer existentes; testes de integração de notificações/Doze/PiP ainda estão pendentes (detalhes em [VALIDACAO.md](devdocs/VALIDACAO.md)). Se `devices -l` mostrar outro identificador, substitua-o nos dois últimos comandos. Aguarde o dispositivo aparecer com estado `device`. Se a compilação falhar, corrija o erro antes de instalar para não executar um APK antigo.

`install -r` atualiza a versão debug preservando seus dados. Use o APK **debug** no emulador x86_64; o release deste projeto é voltado a ARM64. Se o seu Android SDK estiver em outra pasta, ajuste `$ninhoAdb` para o SDK indicado nas configurações do Android Studio ou em `local.properties`.

Com apenas um dispositivo conectado, o Gradle também pode compilar e instalar em uma etapa:

```powershell
.\gradlew.bat :app:installDebug
& $ninhoAdb -e shell am start -n 'app.ninho.android/.MainActivity'
```

Para acompanhar erros durante a execução:

```powershell
& $ninhoAdb -s emulator-5554 logcat -s AndroidRuntime:E
```

O emulador serve para conferir a interface e os fluxos. Desempenho da IA, consumo de memória e compatibilidade dos modelos devem ser avaliados também num celular físico.

### Se o Android Studio mostrar “Dependency verification failed” ao baixar fontes

O erro observado em `:app:detachedConfiguration2` era a ausência de checksums de 106 arquivos de fontes usados pela IDE, incluindo o ZIP de fontes do Gradle. Os artefatos foram conferidos nos repositórios oficiais e seus SHA-256 foram fixados individualmente em `gradle/verification-metadata.xml`. Não foi desativada a verificação.

Depois de atualizar os arquivos do projeto, use **Sync Project with Gradle Files**. A resolução das mesmas fontes pode ser conferida pelo terminal:

```powershell
.\gradlew.bat --init-script scripts/verify-ide-sources.init.gradle verifyIdeSources --console=plain
```

A lista exata, com URLs oficiais e hashes, está em `security/ide-source-artifacts.json`. O comando mantém a política estrita; não limpa dados nem instala o app.

## Abrir e compilar no Windows

Abra esta pasta no Android Studio. O SDK já foi configurado neste computador pelo arquivo local `local.properties`, que é ignorado pelo Git.

No PowerShell, dentro desta pasta:

```powershell
.\gradlew.bat --version
.\gradlew.bat :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :app:lintRelease
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

## Dispositivos e tamanho

A versão release usa ARM64 e otimização R8/remoção de recursos não utilizados. A versão debug também inclui x86_64 para desenvolvimento. O LiteRT-LM incorpora uma biblioteca nativa; seu custo de armazenamento é separado do arquivo de pesos. O desenvolvimento tem como referência celulares ARM64 de alto desempenho. Equivalência de desempenho e de recursos com o iPhone 15 Pro Max precisa ser medida num Android físico.

O aplicativo não solicita acesso à internet. O seletor do sistema concede acesso somente ao arquivo escolhido para importação do modelo. Nenhum emulador, imagem de sistema ou modelo foi instalado para esta implementação.

## Git e dados locais

O `.gitignore` exclui caches, outputs, configurações do computador, IDE, APKs, backups, PDFs pessoais, `.env` e chaves de assinatura. O wrapper Gradle, incluindo seu JAR verificado, faz parte dos fontes e deve ser versionado.

Arquivos de estudo devem permanecer fora do repositório. As regras de ignore não detectam dados pessoais colados dentro de código ou documentação; revise a lista de arquivos antes do seu commit.

## Validação

Consulte [VALIDACAO.md](devdocs/VALIDACAO.md) para os comandos executados e seus limites. Os testes de comportamento verificam timer, relógio, retomada e recomendações. Compilação e lint não equivalem a testes funcionais/visuais ou inferência no telefone.

Consulte [SECURITY.md](devdocs/SECURITY.md) para a auditoria das dependências, os comandos de verificação e os testes do pacote de produção. O Dependabot está preparado para propor atualizações semanais do Gradle no GitHub, sem fusão automática; nenhuma configuração remota foi ativada nesta alteração.
