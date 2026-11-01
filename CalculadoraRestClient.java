import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import java.net.HttpURLConnection;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

public class CalculadoraRestClient extends JFrame {

    // Configurações de rede e retry
    private static final String URL_SERVIDOR = "http://localhost:8080/calculadora"; // servidor Flask
    private static final int MAX_TENTATIVAS = 4;
    private static final long ESPERA_RETRY_MS = 3000;   // T = 3s entre tentativas
    private static final int TIMEOUT_CONEXAO_MS = 15000;
    private static final int TIMEOUT_LEITURA_MS = 60000;

    private JTextField operador1TextField;
    private JTextField operador2TextField;
    private JComboBox<String> operacaoComboBox;
    private JTextField expressaoTextField;
    private JTextField resultadoTextField;
    private JTextArea logTextArea;
    private JButton calcularButton;
    private JButton exprClienteButton;
    private JButton exprServidorButton;

    public CalculadoraRestClient() {
        super("Calculadora REST Client");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel panel = new JPanel(new BorderLayout());
        add(panel);

        JPanel topo = new JPanel(new BorderLayout());
        panel.add(topo, BorderLayout.NORTH);

        // Linha 1: operação individual
        JPanel inputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topo.add(inputPanel, BorderLayout.NORTH);

        inputPanel.add(new JLabel("Operador 1:"));
        operador1TextField = new JTextField(8);
        inputPanel.add(operador1TextField);

        inputPanel.add(new JLabel("Operador 2:"));
        operador2TextField = new JTextField(8);
        inputPanel.add(operador2TextField);

        inputPanel.add(new JLabel("Operação:"));
        operacaoComboBox = new JComboBox<>(new String[] {"soma", "subtração", "multiplicação", "divisão"});
        inputPanel.add(operacaoComboBox);

        calcularButton = new JButton("Calcular");
        calcularButton.addActionListener(e -> executarAsync(this::calcularOperacaoSimples));
        inputPanel.add(calcularButton);

        // Linha 2: expressão
        JPanel exprPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topo.add(exprPanel, BorderLayout.SOUTH);

        exprPanel.add(new JLabel("Expressão:"));
        expressaoTextField = new JTextField(22);
        exprPanel.add(expressaoTextField);

        exprClienteButton = new JButton("Calcular (cliente)");
        exprClienteButton.addActionListener(e -> executarAsync(this::calcularExpressaoNoCliente));
        exprPanel.add(exprClienteButton);

        exprServidorButton = new JButton("Calcular (servidor)");
        exprServidorButton.addActionListener(e -> executarAsync(this::calcularExpressaoNoServidor));
        exprPanel.add(exprServidorButton);

        logTextArea = new JTextArea(8, 50);
        logTextArea.setEditable(false);
        panel.add(new JScrollPane(logTextArea), BorderLayout.CENTER);

        JPanel outputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.add(outputPanel, BorderLayout.SOUTH);

        outputPanel.add(new JLabel("Resultado da Operação:"));
        resultadoTextField = new JTextField(15);
        resultadoTextField.setEditable(false);
        outputPanel.add(resultadoTextField);

        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // Execução em background (evita travar a GUI durante os retries)
    private void executarAsync(Callable<String> tarefa) {
        setBotoesHabilitados(false);
        resultadoTextField.setText("calculando...");
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return tarefa.call();
            }

            @Override
            protected void done() {
                try {
                    resultadoTextField.setText(get());
                } catch (Exception ex) {
                    Throwable causa = (ex.getCause() != null) ? ex.getCause() : ex;
                    resultadoTextField.setText("erro");
                    log("ERRO: " + causa.getMessage());
                } finally {
                    setBotoesHabilitados(true);
                }
            }
        }.execute();
    }

    private void setBotoesHabilitados(boolean habilitado) {
        calcularButton.setEnabled(habilitado);
        exprClienteButton.setEnabled(habilitado);
        exprServidorButton.setEnabled(habilitado);
    }

    private void log(String msg) {
        System.out.println(msg);
        SwingUtilities.invokeLater(() -> logTextArea.append(msg + "\n"));
    }

    //Casos de uso

    // Operação individual: uma chamada REST
    private String calcularOperacaoSimples() throws Exception {
        double a = Double.parseDouble(operador1TextField.getText().trim());
        double b = Double.parseDouble(operador2TextField.getText().trim());
        int operacao = codigoOperacao((String) operacaoComboBox.getSelectedItem());
        return formatar(operacaoRemota(operacao, a, b));
    }

    // Abordagem 1: o cliente interpreta a expressão
    // cada operador vira uma chamada ao servidor
    private String calcularExpressaoNoCliente() throws Exception {
        String expr = expressaoTextField.getText().trim();
        log("Expressão (cliente): " + expr);
        return formatar(new ParserExpressao(expr).avaliar());
    }

    // Abordagem 2: a expressão inteira é enviada ao servidor (uma requisição)
    private String calcularExpressaoNoServidor() throws Exception {
        String expr = expressaoTextField.getText().trim();
        log("Expressão (servidor): " + expr);
        String corpo = "operacao=5&expressao=" + URLEncoder.encode(expr, "UTF-8"); // operacao 5 = expressão
        String json = requisicaoComRetry(URL_SERVIDOR, corpo);
        return formatar(extrairResultado(json));
    }

    // Comunicação HTTP-REST

    // POST /calculadora com operacao=1..4, oper1 e oper2 (form-urlencoded)
    private double operacaoRemota(int operacao, double a, double b) throws Exception {
        if (operacao == 4 && b == 0) {
            throw new ArithmeticException("Divisão por zero");
        }
        String corpo = "operacao=" + operacao + "&oper1=" + formatar(a) + "&oper2=" + formatar(b);
        return extrairResultado(requisicaoComRetry(URL_SERVIDOR, corpo));
    }

    // POST com política de Retry: falhas de rede/timeout/5xx esperam T segundos e tentam de novo; 4xx falha direto
    private String requisicaoComRetry(String urlStr, String corpo) throws Exception {
        IOException ultimoErro = null;
        for (int tentativa = 1; tentativa <= MAX_TENTATIVAS; tentativa++) {
            try {
                log("POST " + urlStr + " (tentativa " + tentativa + "/" + MAX_TENTATIVAS + ")");
                HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(TIMEOUT_CONEXAO_MS);
                conn.setReadTimeout(TIMEOUT_LEITURA_MS);
                conn.setRequestProperty("Accept", "application/json");

                if (corpo != null) { // corpo form-urlencoded (caso da expressão)
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                    byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
                    conn.setFixedLengthStreamingMode(bytes.length);
                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(bytes);
                    }
                }

                int status = conn.getResponseCode();
                if (status >= 500) {
                    throw new IOException("Servidor respondeu HTTP " + status); // transitório -> retry
                }
                InputStream is = (status >= 400) ? conn.getErrorStream() : conn.getInputStream();
                String resposta;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    resposta = reader.lines().collect(Collectors.joining());
                }
                if (status >= 400) {
                    throw new IllegalArgumentException("HTTP " + status + ": " + resposta); // sem retry
                }
                log("Resposta (HTTP " + status + "): " + resposta);
                return resposta;
            } catch (IOException e) {
                ultimoErro = e;
                log("Falha: " + e.getMessage());
                if (tentativa < MAX_TENTATIVAS) {
                    log("Aguardando " + (ESPERA_RETRY_MS / 1000) + "s para tentar novamente...");
                    Thread.sleep(ESPERA_RETRY_MS);
                }
            }
        }
        throw new IOException("Servidor indisponível após " + MAX_TENTATIVAS + " tentativas: "
                + (ultimoErro != null ? ultimoErro.getMessage() : "erro desconhecido"));
    }

    // Extrai o número do campo "resultado"
    //  se o servidor devolveu erro, lança a mensagem
    private double extrairResultado(String json) {
        Matcher erro = Pattern.compile("\"erro\"\\s*:\\s*\"(.*?)\"\\s*[,}]").matcher(json);
        if (erro.find()) {
            throw new IllegalArgumentException(decodificarUnicode(erro.group(1)));
        }
        Matcher m = Pattern.compile("\"resultado\"\\s*:\\s*\"?(-?[0-9.eE+\\-]+)\"?").matcher(json);
        if (!m.find()) {
            throw new IllegalStateException("Resposta sem campo 'resultado': " + json);
        }
        return Double.parseDouble(m.group(1));
    }

    //Utilitários

    // O Flask escapa acentos no JSON (\\u00e3); converte de volta para o caractere
    private String decodificarUnicode(String texto) {
        Matcher m = Pattern.compile("\\\\u([0-9a-fA-F]{4})").matcher(texto);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf((char) Integer.parseInt(m.group(1), 16))));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    // Código da operação esperado pelo servidor
    private int codigoOperacao(String exibicao) {
        switch (exibicao) {
            case "subtração":     return 2;
            case "multiplicação": return 3;
            case "divisão":       return 4;
            default:              return 1;
        }
    }

    private String formatar(double d) {
        if (d == Math.rint(d) && Math.abs(d) < 1e15) {
            return Long.toString((long) d);
        }
        return BigDecimal.valueOf(d).toPlainString();
    }

    // ---- Parser de expressões (abordagem 1) ----
    // expr  -> termo (('+' | '-') termo)*
    // termo -> fator (('*' | '/') fator)*
    // fator -> número | '(' expr ')' | '-' fator
    private class ParserExpressao {
        private final String s;
        private int pos = 0;

        ParserExpressao(String expr) {
            this.s = expr.replaceAll("\\s+", "");
        }

        double avaliar() throws Exception {
            double v = expr();
            if (pos != s.length()) {
                throw new IllegalArgumentException("Caractere inesperado na posição " + pos + ": '" + s.charAt(pos) + "'");
            }
            return v;
        }

        private double expr() throws Exception {
            double v = termo();
            while (pos < s.length() && (s.charAt(pos) == '+' || s.charAt(pos) == '-')) {
                char op = s.charAt(pos++);
                v = operacaoRemota(op == '+' ? 1 : 2, v, termo());
            }
            return v;
        }

        private double termo() throws Exception {
            double v = fator();
            while (pos < s.length() && (s.charAt(pos) == '*' || s.charAt(pos) == '/')) {
                char op = s.charAt(pos++);
                v = operacaoRemota(op == '*' ? 3 : 4, v, fator());
            }
            return v;
        }

        private double fator() throws Exception {
            if (pos >= s.length()) {
                throw new IllegalArgumentException("Expressão incompleta");
            }
            char c = s.charAt(pos);
            if (c == '(') {
                pos++;
                double v = expr();
                if (pos >= s.length() || s.charAt(pos) != ')') {
                    throw new IllegalArgumentException("Parêntese não fechado");
                }
                pos++;
                return v;
            }
            if (c == '-') { // menos unário: 0 - fator
                pos++;
                return operacaoRemota(2, 0, fator());
            }
            int inicio = pos;
            while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) {
                pos++;
            }
            if (inicio == pos) {
                throw new IllegalArgumentException("Caractere inesperado na posição " + pos + ": '" + c + "'");
            }
            return Double.parseDouble(s.substring(inicio, pos));
        }
    }

    public static void main(String[] args) {
        EventQueue.invokeLater(CalculadoraRestClient::new);
    }
}
