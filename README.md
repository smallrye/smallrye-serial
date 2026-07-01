[![SmallRye Build](https://github.com/smallrye/smallrye-serial/workflows/SmallRye%20Build/badge.svg?branch=main)](https://github.com/smallrye/smallrye-serial/actions?query=workflow%3A%22SmallRye+Build%22)
[![Maven Central](https://img.shields.io/maven-central/v/io.smallrye.serial/smallrye-serial?color=green)](https://search.maven.org/search?q=g:io.smallrye.serial)
[![License](https://img.shields.io/github/license/smallrye/smallrye-serial.svg)](http://www.apache.org/licenses/LICENSE-2.0)

# SmallRye Serial

A safe and efficient Java serialization library that captures serialized object graphs
into a structured, inspectable intermediate representation rather than an opaque byte stream.

## Overview

SmallRye Serial serializes Java objects into a tree of `Serialized` nodes that can be
examined, transformed, or stored before being deserialized back into live objects.
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

Create a `SerialContext`, then use it to serialize and deserialize objects:

```java
// Build a context with the default serialization providers
SerialContext ctx = SerialContext.builder()
    .addDefaultProviders()
    .build();

// Serialize an object into its intermediate representation
Serialized serialized = ctx.serialize(myObject);

// Deserialize back into a live object
Object restored = ctx.deserialize(serialized);
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
