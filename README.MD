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
        <id>jitpack</id>
        <url>https://jitpack.io</url>
        <snapshots>
            <enabled>true</enabled>
        </snapshots>
        <releases>
            <enabled>true</enabled>
        </releases>
    </repository>
</repositories>
```

Add dependency

```xml
<dependency>
  <groupId>com.github.DenisLAD</groupId>
  <artifactId>spring-ai-gigachat</artifactId>
  <version>0.0.4</version>
</dependency>
```