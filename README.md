## Description

Provides a set of [string template processors](https://openjdk.org/jeps/430) for 
[MongoDB Extended JSON](https://www.mongodb.com/docs/manual/reference/mongodb-extended-json/)
that produce BSON documents.
         
## Motivation

There are cases where one might have a MongoDB query filter, update, aggregation pipeline, or
even a document to insert into a collection, that is already in Extended JSON format, and the 
application just needs to parse it into BSON so that it can be used with the MongoDB Java driver.

If there are some keys or values in that document that vary based on program input, then a string template
processor designed explicitly to parse Extended JSON may be useful.

With string templates in preview as of Java 21 and likely to be included in a future release of the JDK,
this is a good time to experiment with a string template processor for Extended JSON.

## Usage

The `EXT_JSON` template processor processes a template conforming to a single Extended JSON document. It 
produces a value of type `org.bson.conversions.Bson`, which can be passed as an argument to any [MongoDB
Java driver](https://github.com/mongodb/mongo-java-driver) method that accepts a BSON document parameter, 
such as `MongoCollection#find`.

```java
import org.bson.conversions.Bson;
import org.mongodb.template.processor.EXT_JSON;

class Test {
    Bson createQueryFilter(int minAge) {
       return EXT_JSON."""
               {
                  age : {$gte : \{ minAge } }
               }""";
    }
}
```

The `EXT_JSON_LiST` template processor processes a template conforming to an Extended JSON array containing
Extended JSON document elements.  It produces a value of type `List<Bson>`.  This is useful for creating
[MongoDB aggregation pipelines](https://www.mongodb.com/docs/manual/core/aggregation-pipeline/), as the
`MongoCollection#aggregate` methods accepts a parameter of type `List<Bson>` as the pipeline.

```java
import org.bson.conversions.Bson;
import org.mongodb.template.processor.EXT_JSON;

class Test {
    List<Bson> createAggregationPipeline(String size) {
       return EXT_JSON_LIST."""
               [
                  {
                     $match: { size: \{ size } }
                  },
                  {
                     $group: { _id: "$name", totalQuantity: { $sum: "$quantity" } }
                  }
               ]""";
    }
}
```

Internally, these template processors use a 
[CodecRegistry](https://www.mongodb.com/docs/drivers/java/sync/current/fundamentals/data-formats/codecs/#codecregistry)
to convert each template expression to a BSON value.  By default, it uses `Bson.DEFAULT_CODEC_REGISTRY`, which 
contains codecs for most Java types that one would need to convert to BSON.  But there are circumstances where
one might need an application-defined codec registry, and for those situations one can create a template
processor with an application-provided codec registry.  For example, an application-provided codec registry is
required for template expressions of type `UUID`, for which you have to specify how the UUID is encoded to a 
BSON Binary value:

```java
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.UuidRepresentation;
import org.bson.conversions.Bson;
import org.mongodb.template.processor.EXT_JSON;

class Test {
    Bson createQueryFilter(UUID uuid) {
        return EXT_JSON(CodecRegistries.withUuidRepresentation(Bson.DEFAULT_CODEC_REGISTRY, 
                UuidRepresentation.STANDARD))."""
                {
                   _id : \{ uuid } }
                }""";
    }
}
```