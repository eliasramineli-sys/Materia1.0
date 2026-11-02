# Validação — Materia 1.0.0

Verificação em 02/11/2026, com OpenJDK 17 em Linux.

## Resultados observados

| Conjunto | Testes | Falhas | Erros |
| --- | ---: | ---: | ---: |
| CsvAndNumbersTest | 21 | 0 | 0 |
| InventoryTest | 20 | 0 | 0 |
| UiSmokeTest | 1 | 0 | 0 |

Total: **42 testes executados e aprovados**, incluindo um teste de interface com vários fluxos. Build Maven `verify` concluído, com JAR executável e dependências reunidas.

Regras verificadas: precisão decimal, custo médio ponderado, saída sem saldo, ajuste por saldo final, duplicidade de código/documento, vínculo de fornecedor, arquivamento, persistência após reabertura, bloqueio de alteração da unidade com histórico, importação sem gravação parcial, CSV com aspas/múltiplas linhas, proteção de campos de planilha, escape HTML, backup/restauração, rejeição de backup inválido e caminhos com espaços/acentos.

Interface executada em desktop virtual: navegação nas seis telas, cadastro de fornecedor, cadastro de material com saldo inicial e lançamento de entrada. Capturas inspecionadas dos temas claro/escuro, formulário de material, entrada e configurações em janela de 1100 × 720. Ajustados o gráfico, a altura dos campos e o contraste do tema escuro.

## Distribuição Windows

- `Materia.exe` compilado a partir de `native/launcher.c` como PE32+ GUI x86-64.
- Imports do iniciador limitados a KERNEL32.dll, msvcrt.dll e USER32.dll.
- Java Temurin Windows x64 17.0.20.1+1 incluído, sem modificação. SHA-256 do download conferido com os metadados oficiais; ZIP do runtime verificado.
- JAR inclui a biblioteca SQLite e suas implementações nativas, inclusive Windows x64.
- Pacote portátil começa sem banco ou dados de usuário. Dados de demonstração são opcionais.

**Limite da validação:** o ambiente disponível é Linux. O executável foi gerado e inspecionado, mas sua abertura nativa no Windows, SmartScreen e diálogo de impressão do Windows não foram testados neste ambiente. O workflow Windows incluído não foi executado remotamente. A validação Java e a inspeção da interface não equivalem a uma execução Windows.
