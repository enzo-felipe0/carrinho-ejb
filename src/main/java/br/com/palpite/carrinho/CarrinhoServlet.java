package br.com.palpite.carrinho;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

/**
 * Ponte entre a sessao HTTP e o EJB @Stateful: o proxy do carrinho fica na
 * HttpSession, entao cada sessao conversa com a sua propria instancia.
 *
 * ?acao=adicionar&produto=Mouse&qtd=1 | ?acao=listar | ?acao=finalizar
 */
@WebServlet("/carrinho")
public class CarrinhoServlet extends HttpServlet {

    private static final String ATTR = "carrinhoEJB";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/plain;charset=UTF-8");
        PrintWriter out = resp.getWriter();
        String acao = req.getParameter("acao") == null ? "listar" : req.getParameter("acao");

        try {
            switch (acao) {
                case "adicionar" -> {
                    String produto = req.getParameter("produto");
                    int qtd = Integer.parseInt(req.getParameter("qtd") == null ? "1" : req.getParameter("qtd"));
                    CarrinhoRemote carrinho = carrinhoDaSessao(req);
                    carrinho.adicionarItem(produto, qtd);
                    out.println("Adicionado: " + produto + " x" + qtd);
                    imprimir(out, req, carrinho);
                }
                case "finalizar" -> {
                    HttpSession sessao = req.getSession();
                    CarrinhoRemote carrinho = (CarrinhoRemote) sessao.getAttribute(ATTR);
                    if (carrinho == null) {
                        out.println("Nenhum carrinho nesta sessao.");
                        return;
                    }
                    out.println("== CHECKOUT ==");
                    out.print(carrinho.finalizarCompra());
                    // o @Remove destruiu o bean: descarta o proxy morto da sessao
                    sessao.removeAttribute(ATTR);
                    out.println("Bean @Stateful destruido. Proximo 'adicionar' cria um novo.");
                }
                default -> imprimir(out, req, carrinhoDaSessao(req));
            }
        } catch (IllegalArgumentException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.println("ERRO: " + e.getMessage());
        } catch (jakarta.ejb.EJBException e) {
            // excecao lancada dentro do EJB chega embrulhada no cliente remoto
            Throwable causa = e.getCause() != null ? e.getCause() : e;
            resp.setStatus(causa instanceof IllegalArgumentException
                    ? HttpServletResponse.SC_BAD_REQUEST
                    : HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.println("ERRO: " + causa.getMessage());
        } catch (NamingException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.println("ERRO no lookup do EJB: " + e.getMessage());
        }
    }

    /** Primeira chamada da sessao faz o lookup; as seguintes reusam o mesmo bean. */
    private CarrinhoRemote carrinhoDaSessao(HttpServletRequest req) throws NamingException {
        HttpSession sessao = req.getSession();
        CarrinhoRemote carrinho = (CarrinhoRemote) sessao.getAttribute(ATTR);
        if (carrinho == null) {
            String jndi = "java:module/CarrinhoBean!" + CarrinhoRemote.class.getName();
            carrinho = (CarrinhoRemote) InitialContext.doLookup(jndi);
            sessao.setAttribute(ATTR, carrinho);
        }
        return carrinho;
    }

    private void imprimir(PrintWriter out, HttpServletRequest req, CarrinhoRemote carrinho) {
        List<String> itens = carrinho.listarItens();
        out.println("Sessao: " + req.getSession().getId());
        out.println("Bean  : " + String.format("%x", System.identityHashCode(carrinho)));
        out.println("Itens : " + (itens.isEmpty() ? "(vazio)" : String.join(", ", itens)));
    }
}
