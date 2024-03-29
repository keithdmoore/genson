[![Build Status](https://travis-ci.org/owlike/genson.svg?branch=master)](https://travis-ci.org/owlike/genson)

#Genson

Genson is a complete json <-> java conversion library, providing full databinding, streaming and much more.

Gensons main strengths?

 - Its modular and configurable architecture.
 - Speed and controlled small memory foot print making it scale.
 - Last but not least it is easy to use and works out of the box!

##Online Documentation

Checkout our new website - <http://owlike.github.io/genson/>!


The old website at <http://code.google.com/p/genson/>, hosts the documentation and javadoc of the latest release (0.99).
But starting with 1.0 everything will be moved to github and the new website.

##Motivation

You might wonder, why create another Json databinding lib for Java?
Well...most libraries, miss of important features or have a lot of features but you can hardly add new features by yourself.
Gensons initial motivation is to solve those problems by trying to come with useful features out of the box and stay as much as possible open to extension.


##Features you will like

  - Easy to use, fast, highly configurable, lightweight and all that into a single small jar!
  - Full databinding and streaming support for efficient read/write
  - Support for polymorphic types (able to deserialize to an unknown type)
  - Does not require a default no arg constructor and really passes the values not just null, encouraging immutability. It can even be used with factory methods instead of constructors!
  - Full support for generic types
  - Easy to filter/include properties without requiring the use of annotations or mixins
  - Genson provides a complete implementation of JSR 353
  - Starting with Genson 0.95 JAXB annotations and types are supported!
  - Automatic support for JSON in JAX-RS implementations
  - Serialization and Deserialization of maps with complex keys

##Goals

 - Be as much extensible as possible by allowing users to add new features in a clean and easy way. Genson applies the philosophy that "We can not think of every use case, so give to users the ability to do it by them self in a easy way".
 - Provide an easy to use API.
 - Try to be as fast and scalable or even faster than the most performant librairies.
 - Full support of Java generics.
 - Provide the ability to work with classes of which you don't have the source code.
 - Provide an efficient streaming API.


##Basic example

```java
Map<String, Object> person = new HashMap<String, Object>() {{
  put("name", "Foo");
  put("age", 28);
}};

// create an instance of Genson with default configuration
Genson genson = new Genson();

// {"age":28,"name":"Foo"}
String json = genson.serialize(person);
```

##Configuration

You want to customize Genson? It's easy via the GensonBuilder, allowing you build customized instances of Genson.

```java
// For example we can configure Genson to output indented JSON and to deserialize to constructors with arguments
Genson genson = new GensonBuilder().useIndentation(true).useConstructorWithArguments(true).create;
genson.serialize(person);
```

