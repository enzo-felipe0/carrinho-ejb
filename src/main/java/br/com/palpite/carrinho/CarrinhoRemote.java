package br.com.palpite.carrinho;

import jakarta.ejb.Remote;

import java.util.List;

/**
 * @Remote: interface de negocio visivel fora da JVM. O cliente recebe um proxy
 * que serializa os parametros e envia a chamada pela rede.
 */
@Remote
public interface CarrinhoRemote {

    void adicionarItem(String produto, int quantidade);

    List<String> listarItens();

    String finalizarCompra();
}
