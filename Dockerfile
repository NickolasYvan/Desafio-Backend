# ---- Etapa 1: compila o projeto (Maven + JDK 21) ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copiar so o pom primeiro permite reaproveitar o cache das dependencias quando apenas o codigo muda.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package

# ---- Etapa 2: imagem final, so com o Java (JRE) e o jar ----
FROM eclipse-temurin:21-jre
WORKDIR /app

# Nao roda como root dentro do container.
RUN useradd --system --no-create-home app
COPY --from=build /build/target/solicitacoes-*.jar app.jar
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
