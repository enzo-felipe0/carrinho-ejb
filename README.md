# Carrinho de Compras com EJB — Jakarta EE 10 + WildFly

Exemplo didático de **sistemas distribuídos com EJB**: um estoque `@Singleton`
(compartilhado por todos) e um carrinho `@Stateful` (um por cliente), expostos
por um servlet e publicados como EJB remoto. Roda no WildFly 31 (Java 17) via
Docker, sem banco de dados — o foco é o container EJB.

## Arquivos

```
pom.xml                                        war, Java 17, só jakartaee-api 10 (provided)
Dockerfile                                     WildFly 31 + target/carrinho.war
src/main/java/br/com/palpite/carrinho/
  EstoqueBean.java        @Singleton   estoque: Notebook 5, Mouse 10, Teclado 8
  CarrinhoRemote.java     @Remote      interface de negócio do carrinho
  CarrinhoBean.java       @Stateful    itens do cliente + @EJB estoque + @Remove
  CarrinhoServlet.java    @WebServlet  /carrinho — guarda o EJB na HttpSession
src/main/webapp/index.html             página na raiz com links para as ações
```

Não há `web.xml` nem `beans.xml`: tudo é descoberto por anotação.

## Como rodar

Só precisa de Docker.

```bash
# 1) compila o war (Maven em container; -u evita arquivos com dono root)
docker run --rm -u "$(id -u):$(id -g)" -e HOME=/tmp \
  -v "$PWD":/app -v "$HOME/.m2":/tmp/.m2 -w /app \
  maven:3.9-eclipse-temurin-17 mvn -B -Dmaven.repo.local=/tmp/.m2 package

# 2) monta a imagem com o WildFly + war
docker build -t carrinho-ejb .

# 3) sobe na porta 8080
docker run -d --name carrinho -p 8080:8080 carrinho-ejb
docker logs -f carrinho      # espere: Deployed "carrinho.war" (~15s)
```

O war se chama `carrinho.war`, então o contexto é `/carrinho` e o servlet
mapeia `/carrinho` → URL final `http://localhost:8080/carrinho/carrinho`.

Para testar no navegador, abra <http://localhost:8080/carrinho/> — a página raiz
tem links para as três ações.

## Testando com curl

As flags `-c cookies.txt -b cookies.txt` gravam e reenviam o cookie de sessão.
**Sem elas cada `curl` é um cliente novo**, o container cria outro `@Stateful`
e o carrinho aparece sempre vazio.

```bash
U=http://localhost:8080/carrinho/carrinho

curl -s -c cookies.txt -b cookies.txt "$U?acao=adicionar&produto=Notebook&qtd=2"
curl -s -c cookies.txt -b cookies.txt "$U?acao=adicionar&produto=Mouse&qtd=1"
curl -s -c cookies.txt -b cookies.txt "$U?acao=listar"
curl -s -c cookies.txt -b cookies.txt "$U?acao=finalizar"
curl -s -c cookies.txt -b cookies.txt "$U?acao=listar"
```

Saída real:

```
Adicionado: Notebook x2
Sessao: I5-WnoIC40_9psD-M_0tLCPKa202huY7tpBfYQrU
Bean  : 29d739c2
Itens : Notebook x2

Adicionado: Mouse x1
Bean  : 29d739c2                     <- mesmo bean: estado mantido
Itens : Notebook x2, Mouse x1

Sessao: I5-WnoIC40_9psD-M_0tLCPKa202huY7tpBfYQrU
Bean  : 29d739c2
Itens : Notebook x2, Mouse x1

== CHECKOUT ==
OK          Notebook x2 | restante: 3
OK          Mouse x1 | restante: 9
Bean @Stateful destruido. Proximo 'adicionar' cria um novo.

Bean  : a7bfc8a                      <- outro bean, carrinho vazio
Itens : (vazio)
```

## O que acontece em cada requisição

```
curl ?acao=adicionar&produto=Notebook&qtd=2
 │
 ├─ 1. Undertow recebe GET /carrinho/carrinho e associa a HttpSession (cookie JSESSIONID)
 │
 ├─ 2. CarrinhoServlet: procura o atributo "carrinhoEJB" na sessão; na 1ª vez está vazio
 │
 ├─ 3. InitialContext.doLookup("java:module/CarrinhoBean!...CarrinhoRemote")
 │       o container devolve um PROXY que implementa CarrinhoRemote
 │
 ├─ 4. o proxy vai para sessao.setAttribute("carrinhoEJB", proxy)
 │       -> esta sessão HTTP fica ligada a UM bean @Stateful
 │
 ├─ 5. proxy.adicionarItem("Notebook", 2)
 │       o container acha (ou cria) a instância @Stateful desta conversa,
 │       injeta o @EJB EstoqueBean e guarda "Notebook=2" no Map do bean
 │
 └─ 6. resposta text/plain
```

Nas chamadas seguintes os passos 3 e 4 não acontecem: o proxy já está na sessão.

No `?acao=finalizar`, `finalizarCompra()` percorre os itens chamando
`estoqueBean.baixarEstoque(...)`. Cada baixa roda sob `@Lock(WRITE)` no
`@Singleton`, então o par "ler saldo + subtrair" é atômico: duas compras
simultâneas da última unidade não vendem a mesma unidade duas vezes. Ao retornar,
o `@Remove` faz o container **destruir** a instância `@Stateful`; o servlet tira
o proxy da sessão, e o próximo `adicionar` cria um carrinho novo e vazio.

## As anotações

| Anotação | Papel |
|---|---|
| `@Singleton` | uma instância para toda a aplicação: o estoque é único |
| `@ConcurrencyManagement(CONTAINER)` | o container serializa o acesso; não há `synchronized` no código |
| `@Lock(READ)` | várias leituras simultâneas (`consultarEstoque`) |
| `@Lock(WRITE)` | acesso exclusivo, uma thread por vez (`baixarEstoque`) |
| `@Stateful` | uma instância por cliente, mantida entre chamadas |
| `@Remote` | interface visível fora da JVM; o cliente usa um proxy serializável |
| `@EJB` | injeta o `EstoqueBean` dentro do carrinho |
| `@Remove` | destrói o `@Stateful` quando `finalizarCompra()` retorna |
| `@WebServlet("/carrinho")` | registra o servlet sem `web.xml` |

## O que observar

- **`Bean:` igual** entre as chamadas → o estado do carrinho está no servidor, no `@Stateful`.
- **`Bean:` diferente** depois do checkout → o `@Remove` destruiu a instância.
- **`restante: 3` e `9`** → o `@Singleton` foi baixado de verdade.
- **Duas sessões** (`-c a.txt` / `-c b.txt`) mostram carrinhos independentes sobre o mesmo estoque.
- No log do deploy (`docker logs carrinho | grep WFLYEJB0473`) o container lista os nomes JNDI
  do bean: `java:module/...`, `java:app/...`, `java:global/...` e `java:jboss/exported/...`
  (este último é o que um cliente em outra JVM usaria).

## Se algo der errado

| Sintoma | Causa |
|---|---|
| `docker build` falha no `COPY target/carrinho.war` | o war não existe: rode o passo 1 antes |
| `HTTP 404` | o deploy ainda não terminou (`docker logs carrinho`) ou a URL não é `/carrinho/carrinho` |
| `HTTP 403` na raiz | o war não tem `index.html`; aqui ele existe. Se você removeu, use `/carrinho/carrinho` |
| Stack trace no `docker logs` | se for `IllegalArgumentException: informe produto...`, é só o 400 de uma chamada inválida (o WildFly loga a exceção do EJB); o servidor continua normal |
| Carrinho sempre vazio | faltou `-c cookies.txt -b cookies.txt` em algum comando |
| `HTTP 400` | `qtd` inválida (precisa ser > 0) ou `produto` vazio |
| `port is already allocated` | outra coisa na 8080: use `-p 8081:8080` e ajuste a variável `U` |

## Parar

```bash
docker rm -f carrinho
```
