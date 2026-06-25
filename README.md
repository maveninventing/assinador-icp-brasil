# Assinador ICP-Brasil para Windows

[![Licença MIT](https://img.shields.io/badge/licen%C3%A7a-MIT-green.svg)](LICENSE)
[![Java 17](https://img.shields.io/badge/Java-17%2B-blue.svg)](https://adoptium.net/)
[![Build](https://github.com/maveninventing/assinador-icp-brasil/actions/workflows/build.yml/badge.svg)](https://github.com/maveninventing/assinador-icp-brasil/actions/workflows/build.yml)

Aplicativo desktop simples, desenvolvido em Java 17, Swing e Apache Maven, para assinar documentos PDF com certificados digitais:

- **A1** instalado no repositório pessoal de certificados do Windows;
- **A3** em token USB ou cartão, quando o middleware do fabricante publica o certificado no repositório do Windows;
- **A3 via PKCS#11**, como alternativa para tokens que não aparecem no repositório do Windows.

## Funcionalidades

- escolha do PDF de origem;
- listagem dos certificados com chave privada no repositório `Windows-MY`;
- carregamento alternativo da DLL PKCS#11 do token;
- visualização das páginas do PDF;
- posicionamento da assinatura visível com o mouse;
- tamanho configurável em milímetros;
- assinatura CMS/CAdES destacada incorporada ao PDF, com SHA-256;
- geração automática de `nome_original_assinado.pdf` na mesma pasta;
- gravação incremental, preservando assinaturas anteriores quando o PDF permitir novas alterações.

## Requisitos para desenvolvimento

- Windows 10 ou 11, 64 bits;
- JDK 17 ou superior, incluindo o comando `jpackage`;
- Apache Maven 3.9 ou superior;
- driver/middleware oficial do certificado A3 instalado.

Confira no terminal:

```bat
java -version
mvn -version
```

## Compilar e executar como JAR

```bat
mvn clean package
run.bat
```

Ou diretamente:

```bat
java -jar target\assinador-icp-brasil-1.0.0.jar
```

## Gerar aplicativo Windows com executável

Execute:

```bat
build-windows.bat
```

O `jpackage` criará uma imagem autocontida, com runtime Java próprio:

```text
dist\Assinador ICP Brasil\Assinador ICP Brasil.exe
```

A pasta inteira de `dist\Assinador ICP Brasil` deve ser distribuída; o `.exe` não funciona sozinho fora dela.

## Uso com certificado A1

1. Importe o certificado A1 normalmente pelo Windows.
2. Abra o aplicativo.
3. Clique em **Atualizar certificados**.
4. Escolha o certificado na lista.

O aplicativo acessa o repositório nativo `Windows-MY`; a senha do arquivo A1 não é solicitada novamente depois que ele está instalado com sua chave privada.

## Uso com token A3

### Opção recomendada: certificado exposto no Windows

1. Instale o middleware oficial do fabricante do token.
2. Conecte o token USB antes de abrir ou atualizar a lista.
3. Clique em **Atualizar certificados**.
4. Escolha o certificado do token.
5. Ao assinar, aguarde a janela de PIN apresentada pelo driver do token.

Essa opção deixa a autenticação e o bloqueio por tentativas sob controle do middleware do fabricante.

### Alternativa: acesso direto por PKCS#11

Clique em **Carregar token PKCS#11...**, selecione a DLL PKCS#11 do fabricante e informe o PIN.

Localizações comuns, que variam por fabricante e versão:

```text
C:\Windows\System32\*.dll
C:\Program Files\<fabricante>\*.dll
C:\Program Files (x86)\<fabricante>\*.dll
```

Não escolha uma DLL aleatória. Consulte a documentação do token para descobrir o módulo PKCS#11 correto. Alguns drivers possuem vários slots; esta versão usa `slotListIndex=0`.

## Observações de segurança e conformidade

- O aplicativo nunca exporta a chave privada. No A3, a operação criptográfica ocorre no token/middleware.
- O PIN informado no modo PKCS#11 é apagado do array em memória após o carregamento do token, mas a sessão do driver permanece aberta enquanto o aplicativo estiver em execução.
- A assinatura usa o subfiltro PDF `ETSI.CAdES.detached`, inclui a cadeia disponível e o atributo `SigningCertificateV2`.
- Esta versão não incorpora carimbo do tempo, respostas OCSP, listas de certificados revogados nem material de validação de longo prazo. Portanto, ela serve como assinatura PDF ICP-Brasil básica, mas **não deve ser apresentada como implementação homologada ou como PAdES-LT/LTA** sem testes formais de conformidade, política de assinatura, validação de cadeia e integração com uma ACT credenciada.
- PDFs criptografados, protegidos por senha ou certificados com restrição DocMDP podem impedir a assinatura.

## Estrutura principal

```text
src/main/java/br/com/privacytools/assinador/
├── App.java
├── certificate/
│   ├── CertificateEntry.java
│   ├── CertificateNameUtils.java
│   └── CertificateService.java
├── signing/
│   ├── CmsSignature.java
│   ├── PdfSignerService.java
│   └── SignaturePlacement.java
├── ui/
│   ├── MainFrame.java
│   └── PdfPreviewPanel.java
└── util/
    └── FileNameUtils.java
```

## Código aberto

Este projeto é software livre e de código aberto, distribuído sob a **Licença MIT**. Você pode usar, copiar, modificar, publicar, distribuir, sublicenciar e comercializar o software, desde que mantenha o aviso de copyright e a licença.

Consulte o arquivo [`LICENSE`](LICENSE).

## Contribuições

Contribuições são bem-vindas. Abra uma issue descrevendo o problema ou a melhoria e, quando possível, envie um pull request com alterações pequenas e objetivas.

Antes de distribuir o aplicativo em ambiente de produção, valide o funcionamento com os modelos de certificados A1/A3 e middlewares utilizados pela sua organização.
