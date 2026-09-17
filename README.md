[![SmallRye Build](https://github.com/smallrye/smallrye-serial/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/smallrye/smallrye-serial/actions?query=workflow%3A%22SmallRye+Build%22)
[![Maven Central](https://img.shields.io/maven-central/v/io.smallrye.serial/smallrye-serial?color=green)](https://search.maven.org/search?q=g:io.smallrye.serial)
[![License](https://img.shields.io/github/license/smallrye/smallrye-serial.svg)](http://www.apache.org/licenses/LICENSE-2.0)

# SmallRye Serial

A safe and efficient Java serialization library that captures serialized object graphs
into a structured, inspectable intermediate representation, with full support for
reading from and writing to standard Java serialization byte streams.

## Overview

SmallRye Serial decouples the serialization process into two layers:

1. **Object Graph to Representation**: Java objects are serialized into a tree of `Serialized` nodes that can be examined, transformed, or filtered.
2. **Representation to Byte Stream**: The `Serialized` representation can be written to or read from the standard Java serialization wire format.

This separation allows for secure class-filtering and graph-inspection before any live Java objects are actually instantiated.

It supports the full range of Java serialization mechanisms:

- `Serializable` classes (including custom `writeObject`/`readObject`)
- `Externalizable` classes
- Records
- Enums
- Proxies
- `writeReplace`/`readResolve`
- Circular and self-referencing object graphs

## Usage

### Maven dependency

Add the following to the `dependencies` section of your `pom.xml`:

```xml
<dependency>
    <groupId>io.smallrye.serial</groupId>
    <artifactId>smallrye-serial</artifactId>
    <version>VERSION</version>
</dependency>
```

Replace `VERSION` with the latest release version.

### Getting started

First, configure a thread-safe `SerialContext` with standard serialization providers:

```java
SerialContext ctx = SerialContext.builder()
    .addDefaultProviders()
    .build();
```

#### 1. Object to/from Intermediate Representation

Use the context to create a `Serializer` or `Deserializer` to convert between Java objects and `Serialized` representation nodes. While `SerialContext` is thread-safe, individual serializers and deserializers are not.

```java
// Create a serializer and serialize an object to its intermediate representation
Serializer serializer = ctx.createSerializer();
Serialized serialized = serializer.serialize(myObject);

// Create a deserializer and deserialize back into a live object
Deserializer deserializer = ctx.createDeserializer();
Object restored = deserializer.deserialize(serialized);
```

#### 2. Intermediate Representation to/from Byte Stream

Use `SerialStreamWriter` and `SerialStreamReader` to write and read the intermediate representation to/from raw streams using the standard Java serialization format (compatible with `ObjectOutputStream`/`ObjectInputStream` wire format).

```java
// Write the representation to a byte stream
try (SerialStreamWriter writer = SerialStreamWriter.builder(outputStream).build()) {
    writer.writeSerialized(serialized);
}

// Read the representation from a byte stream
try (SerialStreamReader reader = SerialStreamReader.builder(inputStream).build()) {
    Serialized readSerialized = reader.readSerialized();
}
```

### JPMS module

The module name is `io.smallrye.serial`.

```java
module my.module {
    requires io.smallrye.serial;
}
```

## Build

```shell
mvn verify
```

## Contributing

Please refer to the SmallRye Wiki for the [Contribution Guidelines](https://github.com/smallrye/smallrye-parent/wiki).

## License

This project is licensed under the [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0).
