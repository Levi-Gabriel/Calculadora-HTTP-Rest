import math  # Verifica se o resultado é finito (sem infinito/NaN)
import logging  # Log no console do servidor

from flask import Flask, request, jsonify  # Flask: servidor web; request: dados do POST; jsonify: resposta JSON

app = Flask(__name__)  # Cria a aplicação web
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(message)s")  # Configura o formato do log

MSG_PARAMS = "Parâmetros inválidos. Envie 'oper1', 'oper2' e 'operacao' via POST."  # Mesma mensagem do servidor PHP
MSG_DIV_ZERO = "Erro: divisão por zero."  # Mesma mensagem do servidor PHP
MSG_OPERACAO = "Operação inválida. Use 1 (soma), 2 (subtração), 3 (multiplicação), 4 (divisão) ou 5 (expressão)."  # Códigos aceitos


def calcular(operacao, a, b):  # Executa as operações básicas 1 a 4
    if operacao == 1:  # Soma
        return a + b  # Devolve a + b
    if operacao == 2:  # Subtração
        return a - b  # Devolve a - b
    if operacao == 3:  # Multiplicação
        return a * b  # Devolve a * b
    if operacao == 4:  # Divisão
        if b == 0:  # Protege contra divisão por zero
            raise ValueError(MSG_DIV_ZERO)  # Erro repassado ao handler
        return a / b  # Devolve a / b
    raise ValueError(MSG_OPERACAO)  # Código desconhecido


class Avaliador:  # Abordagem 2: avalia a expressão inteira no servidor (descida recursiva)
    def __init__(self, expressao):  # Construtor
        self.txt = "".join(expressao.split())  # Remove todos os espaços em branco
        self.pos = 0  # Posição atual de leitura

    def avalia(self):  # Ponto de entrada
        v = self.expr()  # Processa a regra de mais alto nível
        if self.pos != len(self.txt):  # Sobrou texto não consumido: expressão inválida
            raise ValueError(f"Expressão inválida na posição {self.pos}")  # Informa onde parou
        return v  # Devolve o resultado final

    def expr(self):  # expr = termo { (+|-) termo }
        v = self.termo()  # Lê o primeiro termo
        while self.pos < len(self.txt) and self.txt[self.pos] in "+-":  # Enquanto houver + ou -
            c = self.txt[self.pos]  # Lê o operador
            self.pos += 1  # Consome o operador
            d = self.termo()  # Lê o próximo termo
            v = v + d if c == "+" else v - d  # Soma ou subtração
        return v  # Devolve o valor acumulado

    def termo(self):  # termo = fator { (*|/) fator }
        v = self.fator()  # Lê o primeiro fator
        while self.pos < len(self.txt) and self.txt[self.pos] in "*/":  # Enquanto houver * ou /
            c = self.txt[self.pos]  # Lê o operador
            self.pos += 1  # Consome o operador
            d = self.fator()  # Lê o próximo fator
            if c == "*":  # Multiplicação
                v = v * d  # Acumula o produto
            else:  # Divisão
                if d == 0:  # Protege contra divisão por zero
                    raise ValueError(MSG_DIV_ZERO)  # Erro repassado ao handler
                v = v / d  # Acumula o quociente
        return v  # Devolve o valor acumulado

    def fator(self):  # fator = número | '(' expr ')' | '-' fator
        if self.pos >= len(self.txt):  # Fim inesperado do texto
            raise ValueError("Expressão incompleta")  # Erro de sintaxe
        c = self.txt[self.pos]  # Caractere atual
        if c == "-":  # Menos unário (ex.: -5 ou -(2+3))
            self.pos += 1  # Consome o '-'
            return -self.fator()  # Inverte o sinal do fator seguinte
        if c == "(":  # Início de subexpressão entre parênteses
            self.pos += 1  # Consome o '('
            v = self.expr()  # Avalia a subexpressão
            if self.pos >= len(self.txt) or self.txt[self.pos] != ")":  # Exige o ')' correspondente
                raise ValueError("Parêntese não fechado")  # Erro de sintaxe
            self.pos += 1  # Consome o ')'
            return v  # Devolve o valor da subexpressão
        ini = self.pos  # Marca o início do número
        while self.pos < len(self.txt) and (self.txt[self.pos] in "0123456789."):  # Lê dígitos e ponto
            self.pos += 1  # Avança enquanto for parte do número
        if ini == self.pos:  # Nenhum dígito lido
            raise ValueError(f"Número esperado na posição {self.pos}")  # Erro de sintaxe
        try:  # Converte o texto lido para número
            return float(self.txt[ini:self.pos])  # Ex.: "15" ou "2.5"
        except ValueError:  # Texto inválido, como "1.2.3"
            raise ValueError(f"Número inválido: {self.txt[ini:self.pos]}")  # Erro de sintaxe


def erro(mensagem):  # Resposta de erro no mesmo formato do PHP
    return jsonify({"erro": mensagem})  # Corpo {"erro": "..."} com status 200


@app.route("/calculadora", methods=["GET", "POST"])  # Rota única; GET existe só para devolver erro em JSON
def calculadora():  # Handler da calculadora (equivale à página PHP)
    if request.method != "POST":  # Só aceita POST, como pede a atividade
        resp = erro("Método não permitido. Use POST.")  # Corpo de erro em JSON
        resp.status_code = 405  # Status 405 Method Not Allowed
        resp.headers["Allow"] = "POST"  # Informa o método permitido
        return resp  # Encerra o tratamento

    try:  # Lê o código da operação
        operacao = int(request.form.get("operacao", "").strip())  # Converte para inteiro
    except ValueError:  # Ausente ou não numérico
        return erro(MSG_PARAMS)  # Mesma mensagem do PHP

    if operacao == 5:  # Operação 5: expressão completa calculada no servidor
        expressao = request.form.get("expressao", "")  # Lê a expressão enviada pelo cliente
        logging.info("POST operacao=5 expressao=%r", expressao)  # Log de uma única requisição recebida pela rede
        try:  # Avalia a expressão
            resultado = Avaliador(expressao).avalia()  # Cálculo inteiro local
            if not math.isfinite(resultado):  # Resultado fora do intervalo representável em JSON
                raise ValueError("Resultado inválido")  # Evita gerar JSON inválido
        except ValueError as e:  # Erro de cálculo ou sintaxe
            return erro(str(e))  # Devolve a mensagem de erro
        return jsonify({"expressao": expressao, "operacao": operacao, "resultado": resultado})  # Resposta de sucesso

    try:  # Lê os dois operandos
        oper1 = float(request.form.get("oper1", "").strip())  # Primeiro operando
        oper2 = float(request.form.get("oper2", "").strip())  # Segundo operando
        if not (math.isfinite(oper1) and math.isfinite(oper2)):  # Rejeita "nan" e "inf"
            raise ValueError  # Trata como parâmetro inválido
    except ValueError:  # Ausente ou não numérico
        return erro(MSG_PARAMS)  # Mesma mensagem do PHP
    logging.info("POST operacao=%d oper1=%s oper2=%s", operacao, oper1, oper2)  # Log de cada requisição recebida

    try:  # Executa a operação 1 a 4
        resultado = calcular(operacao, oper1, oper2)  # Soma, subtração, multiplicação ou divisão
    except ValueError as e:  # Divisão por zero ou operação inválida
        return erro(str(e))  # Devolve a mensagem de erro
    return jsonify({"oper1": oper1, "oper2": oper2, "operacao": operacao, "resultado": resultado})  # Resposta de sucesso


if __name__ == "__main__":  # Executa apenas quando o arquivo é rodado diretamente
    app.run(host="0.0.0.0", port=8080)  # Sobe o servidor na porta 8080
