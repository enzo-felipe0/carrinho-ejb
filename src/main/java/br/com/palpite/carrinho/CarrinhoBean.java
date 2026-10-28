package br.com.palpite.carrinho;

import jakarta.ejb.EJB;
import jakarta.ejb.Remove;
import jakarta.ejb.Stateful;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Stateful: uma instancia por cliente, viva entre as chamadas - e por isso o
 * carrinho nao se perde a cada requisicao. O @Remove no checkout manda o
 * container destruir a instancia no fim do metodo.
 */
@Stateful
public class CarrinhoBean implements CarrinhoRemote {

    /** Estado do cliente: produto -> quantidade. */
    private final Map<String, Integer> itens = new LinkedHashMap<>();

    /** O estoque e o @Singleton: mesma instancia para todos os carrinhos. */
    @EJB
    private EstoqueBean estoqueBean;

    @Override
    public void adicionarItem(String produto, int quantidade) {
        if (produto == null || produto.isBlank() || quantidade <= 0) {
            throw new IllegalArgumentException("informe produto e quantidade > 0");
        }
        itens.merge(produto.trim(), quantidade, Integer::sum);
    }

    @Override
    public List<String> listarItens() {
        List<String> linhas = new ArrayList<>();
        itens.forEach((produto, qtd) -> linhas.add(produto + " x" + qtd));
        return linhas;
    }

    /** Baixa o estoque de cada item; depois de retornar, este bean e destruido. */
    @Override
    @Remove
    public String finalizarCompra() {
        if (itens.isEmpty()) {
            return "Carrinho vazio.";
        }
        StringBuilder relatorio = new StringBuilder();
        itens.forEach((produto, qtd) -> relatorio
                .append(estoqueBean.baixarEstoque(produto, qtd) ? "OK          " : "SEM ESTOQUE ")
                .append(produto).append(" x").append(qtd)
                .append(" | restante: ").append(estoqueBean.consultarEstoque(produto))
                .append('\n'));
        return relatorio.toString();
    }
}
