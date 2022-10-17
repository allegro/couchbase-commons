[![Build](https://github.com/allegro/couchbase-commons/actions/workflows/ci.yaml/badge.svg?branch=main)](https://github.com/allegro/couchbase-commons/actions/workflows/ci.yaml)
![Maven Central](https://img.shields.io/maven-central/v/pl.allegro.tech.couchbase-commons/couchbase)

# couchbase-commons

## Usage

```
dependencies {
    implementation "pl.allegro.tech.couchbase-commons:couchbase:1.0"
}
```

```kotlin
val bucket = cluster.bucket(bucketName)
val reactiveCollection = bucket.defaultCollection()
val collection = ReactiveCouchbaseCollection(reactiveCollection).withPrefix(prefix)
```

### TypedCouchbaseRepository

```kotlin
val repository = TypedCouchbaseRepository(collection, DTO::class.java, SimpleMeterRegistry())
val dto = DTO("key", "value")

repository.put(dto.id, dto).block()
val actualDto = repository.get(dto.id)
```

### CouchbaseSetRepository

```kotlin
val repository = CouchbaseSetRepository(collection, DTO::class.java, meterRegistry)

val ttl = Duration.ofSeconds(60)
repository.add("somekey", setOf(DTO("id_1", "value_1")), ttl = ttl)
```
