package br.com.palpite.carrinho;

import jakarta.ejb.ConcurrencyManagement;
import jakarta.ejb.ConcurrencyManagementType;
import jakarta.ejb.Lock;
import jakarta.ejb.LockType;
import jakarta.ejb.Singleton;

import java.util.HashMap;
import java.util.Map;

/**
 * @Singleton: uma unica instancia para toda a aplicacao - o estoque da loja,
 * compartilhado por todos os carrinhos. A concorrencia e gerenciada pelo
 * container (@ConcurrencyManagement(CONTAINER)), nao pelo codigo.
 */
@Singleton
@ConcurrencyManagement(ConcurrencyManagementType.CONTAINER)
public class EstoqueBean {

    private final Map<String, Integer> estoque = new HashMap<>(Map.of(
            "Notebook", 5,
            "Mouse", 10,
            "Teclado", 8));

    /** @Lock(READ): varias leituras ao mesmo tempo. */
    @Lock(LockType.READ)
    public int consultarEstoque(String produto) {
        return estoque.getOrDefault(produto, 0);
    }

    /** @Lock(WRITE): acesso exclusivo - ler o saldo e subtrair sem outra thread no meio. */
    @Lock(LockType.WRITE)
    public boolean baixarEstoque(String produto, int quantidade) {
        int atual = estoque.getOrDefault(produto, 0);
        if (quantidade <= 0 || atual < quantidade) {
            return false;
        }
        estoque.put(produto, atual - quantidade);
        return true;
    }
}
