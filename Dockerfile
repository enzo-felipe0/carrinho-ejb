FROM quay.io/wildfly/wildfly:31.0.0.Final-jdk17

# o war precisa existir antes do build (a imagem so copia, nao compila)
COPY target/carrinho.war /opt/jboss/wildfly/standalone/deployments/carrinho.war

EXPOSE 8080
