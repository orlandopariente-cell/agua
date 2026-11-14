# Água – widget de hidratação para Android

App Android (Kotlin + Jetpack Compose) com widget de tela inicial que mostra se você está
**acima ou abaixo** da quantidade de água que deveria ter tomado até agora.

## Funcionalidades

- **Widget (4×2, redimensionável)**
  - Status grande: `+150 mL` (verde, adiantado) ou `-350 mL` (vermelho, atrasado)
  - Tomado / meta, esperado agora, mL/kg e barra de progresso
  - Botões rápidos **100 · 180 · 500 · 700 mL** e **↶ desfazer** (remove o último registro do dia).
    Os botões só registram a água, não abrem o app.
  - Tocar na área de status abre o app
  - Atualiza sozinho a cada ~15 min (o "esperado" cresce ao longo do dia)
- **App**
  - *Hoje*: status, barra com marca do esperado, botões rápidos, valor personalizado,
    desfazer e lista de registros (com apagar)
  - *Histórico*: gráfico dos últimos 14 dias, média de 7 dias, mL/kg, dias com meta atingida
    e lista dos últimos 90 dias
  - *Ajustes*: meta diária, início e fim do dia, peso (com meta sugerida de 35 mL/kg)

## Cálculo

`esperado = meta × (agora − início) / (fim − início)`, limitado entre 0 e a meta.
Status = tomado − esperado.

O dia "vira" no meio do período de descanso (ex.: com 07:00–23:00, vira às 03:00), então um
copo à meia-noite ainda conta para o dia anterior. O fim do dia pode passar da meia-noite.

## Como compilar e instalar

### Opção A – Android Studio (mais simples)
1. Abra a pasta `Agua` no Android Studio (File → Open).
2. Espere o Gradle sincronizar (ele baixa o SDK necessário).
3. Conecte o celular com *Depuração USB* ativada e clique em ▶ Run.

### Opção B – linha de comando
Requer JDK 17 e Android SDK (platform 35). Com o Gradle 8.9 instalado:

```bash
gradle wrapper --gradle-version 8.9
./gradlew assembleDebug
```

O APK fica em `app/build/outputs/apk/debug/app-debug.apk`.

### Publicar uma atualização
Dê dois cliques em `publicar.bat` (ou rode `publicar.bat -Notas "o que mudou"`). O script aumenta a
versão em `version.properties`, compila, cria uma release `vN` no GitHub com o APK e envia o código.
No celular, o app avisa "Atualização disponível" ao abrir (ou em **Ajustes → Verificar atualização**).

Requer JDK, SDK e GitHub CLI em `%USERPROFILE%\AndroidDev` e o repositório configurado em
`aguaUpdateRepo` no `gradle.properties`. As atualizações só instalam se forem assinadas com a mesma
chave (`%USERPROFILE%\.android\debug.keystore`) — faça backup desse arquivo.

### Adicionar o widget
Pressione e segure a tela inicial → **Widgets** → **Água** → arraste para a tela.

## Estrutura

```
app/src/main/java/app/agua/
  data/   Prefs (ajustes), DayMath (cálculo do dia), WaterRepo (SQLite), Models
  widget/ WaterWidget (AppWidgetProvider com botões via broadcast)
  ui/     MainActivity, TodayScreen, HistoryScreen, SettingsScreen
```
