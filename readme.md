# **Manual Rápido - Corretor de Prova App**

## **Objetivo**

Corrigir provas de múltipla escolha escaneadas em PDF de forma automática e gerar relatório em Excel.

---

## **Passo a Passo**

### **1. Abrir a aplicação**

* Execute `ProvaBolhaDetectorMain.java`.
* A janela principal será exibida.

### **2. Selecionar a pasta de provas**

* Clique em **Selecionar pasta**.
* Escolha a pasta com os PDFs das provas.
* O caminho aparecerá no campo de texto.

### **3. Processar provas**

* Clique em **Processar provas**.
* A tabela será preenchida com:

  * **Arquivo**: nome do PDF
  * **Total de questões** detectadas
  * **Respostas detectadas** (ex: `1:A 2:C 3:B`)

### **4. Exportar Excel**

* Clique em **Exportar Excel (.xlsx)**.
* Escolha onde salvar o arquivo.
* Será gerado um relatório com todas as provas processadas.

---

## **Tabela de resultados**

| Coluna               | Significado                        |
| -------------------- | ---------------------------------- |
| Arquivo              | Nome do PDF processado             |
| Total de questões    | Quantidade de respostas detectadas |
| Respostas detectadas | Listagem das respostas por questão |

---

## **Observações**

* Cada questão deve ter apenas **uma alternativa marcada**.
* PDFs de baixa resolução podem afetar a detecção; **150 DPI ou mais recomendado**.
* Apenas **arquivos PDF** são processados.
* Em caso de erro, a tabela exibirá a mensagem na coluna “Respostas detectadas”.

---

## **Dicas rápidas**

* Mantenha os PDFs **claros e alinhados**.
* Se algumas respostas não forem detectadas, ajustes podem ser feitos nos parâmetros internos (`FILL_THRESHOLD`, `MIN_BLOB_AREA`, `MAX_BLOB_AREA`) no código.

---

Se quiser, posso fazer também uma **versão ilustrada**, com screenshots do FXML e setas indicando onde clicar, perfeita para imprimir ou enviar aos professores.

Quer que eu faça essa versão ilustrada?
