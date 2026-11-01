# Calculadora — HTTP-REST 

Calculadora remota com **servidor Flask (Python)** e **cliente Java Swing**, comunicando-se por HTTP/REST.

- Operações: soma, subtração, multiplicação e divisão.
- Expressões com parênteses, em duas abordagens:
  1. **Cliente:** o cliente interpreta a expressão e faz uma chamada ao servidor por operação.
  2. **Servidor:** a expressão inteira é enviada em uma única requisição.
- O cliente implementa **política de Retry** (espera T segundos e tenta de novo).

## Estrutura

```
REST/
├── Calculator_server.py        # Servidor Flask (porta 8080)
├── CalculadoraRestClient.java  # Cliente Java 
└── README.md
```

## Requisitos

| Componente | Requisito |
|---|---|
| Servidor | Python 3.8+ e Flask |
| Cliente | JDK 11+ (`javac` e `java`) |

Verifique as versões instaladas:

```bash
python --version
javac -version
```

Instalação do JDK, se necessário (Ubuntu/Debian): `sudo apt install default-jdk`

## 1. Rodando o servidor (Python)

### Opção A — ambiente virtual (recomendado)

```bash
cd ~/Documentos/REST
python -m venv .venv
source .venv/bin/activate      # Windows: .venv\Scripts\activate
pip install flask
python Calculator_server.py
```

Nos próximos usos, basta ativar o ambiente e rodar:

```bash
cd ~/Documentos/REST
source .venv/bin/activate
python Calculator_server.py
```

### Opção B — Flask instalado no sistema (sem venv)

```bash
# Arch/Manjaro
sudo pacman -S python-flask
# Fedora
sudo dnf install python3-flask
# Ubuntu/Debian
sudo apt install python3-flask
```

Depois, em qualquer terminal:

```bash
python Calculator_server.py
```

## 2. Rodando o cliente (Java)

Abra um **segundo terminal** (o servidor continua rodando no primeiro):

```bash
cd ~/Documentos/REST
javac CalculadoraRestClient.java
java CalculadoraRestClient
```
