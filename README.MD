# Sber GigaChat integration module for Spring AI

[GigaChat API](https://developers.sber.ru/portal/products/gigachat-api)

## Usage

Add repository to your `pom.xml` file

```xml
<repositories>
    <repository>
        <id>central</id>
        <url>https://repo1.maven.org/maven2</url>
    </repository>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/DenisLAD/spring-ai-gigachat</url>
        <snapshots>
            <enabled>true</enabled>
        </snapshots>
    </repository>
</repositories>
```

and add dependency

```xml
<dependency>
  <groupId>org.springframework.ai</groupId>
  <artifactId>spring-ai-gigachat</artifactId>
  <version>0.0.3</version>
</dependency>
```