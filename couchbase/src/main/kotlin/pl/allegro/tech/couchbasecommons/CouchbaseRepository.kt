package pl.allegro.tech.couchbasecommons

import com.couchbase.client.core.error.DocumentNotFoundException
import com.couchbase.client.java.codec.TypeRef
import com.couchbase.client.java.json.JsonObject
import com.couchbase.client.java.kv.GetResult
import com.couchbase.client.java.kv.MutateInSpec
import com.couchbase.client.java.kv.MutationResult
import com.couchbase.client.java.kv.UpsertOptions
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.time.Duration

interface CouchbaseRepository<T> {
    fun get(key: String): Mono<T>
    fun put(key: String, content: T): Mono<Unit>
}

open class TypedCouchbaseRepository<T : Any>(
    private val collection: CouchbaseCollection,
    private val type: Class<T>,
    private val meterRegistry: MeterRegistry
) : CouchbaseRepository<T> {

    override fun get(key: String): Mono<T> {
        return collection.get(key)
            .measure(meterRegistry, "key.read")
            .map { it.contentAs(type) }
    }

    override fun put(key: String, content: T): Mono<Unit> {
        return internalPut(key, content)
    }

    fun putRaw(key: String, value: JsonObject): Mono<Unit> = internalPut(key, value)

    private fun internalPut(key: String, value: Any): Mono<Unit> = collection.upsert(key, value)
        .measure(meterRegistry, "key.write")
        .map { Unit }
}

fun <T> Mono<T>.measure(meterRegistry: MeterRegistry, metricName: String): Mono<T> {
    return Mono.fromSupplier { Timer.start(meterRegistry) }
        .flatMap { timeSample ->
            this.doOnError { meterRegistry.increment("${metricName}_error") }
                .doOnTerminate { timeSample.stop(meterRegistry.timer("cache.couchbase.$metricName")) }
        }
}

private const val MAX_MUTATE_IN_OPERATIONS = 16

class CouchbaseSetRepository<T: CouchbaseSetEntry>(
    private val collection: CouchbaseCollection,
    private val type: Class<T>,
    private val meterRegistry: MeterRegistry
) {

    fun add(key: String, value: T, ttl: Duration): Mono<Unit> = add(key, setOf(value), ttl)

    fun add(key: String, values: Set<T>, ttl: Duration): Mono<Unit> {
        val upsertOptions = UpsertOptions.upsertOptions().also {
            if (!ttl.isZero)
                it.expiry(ttl)
        }
        return Flux.fromIterable(values)
            .flatMap { value ->
                val hash = value.identifier()
                collection.upsert("${key}_$hash", value, upsertOptions)
                    .measure(meterRegistry, "set.write.item")
                    .map { hash }
            }
            .buffer(MAX_MUTATE_IN_OPERATIONS) //couchbase allows making no more than 16 sub-document operations in single request
            .concatMap { valueKeys -> insertKeysToIndex(key, valueKeys) }
            .collectList()
            .measure(meterRegistry, "set.write")
            .map { Unit }
    }

    private fun insertKeysToIndex(collectionKey: String, keys: List<String>): Mono<MutationResult> {
        return collection.mutateIn(
            collectionKey,
            keys.map { singleKey -> MutateInSpec.upsert(singleKey, 1).createPath() })
            .measure(meterRegistry, "set.mutate.dictionary")
            .cast(MutationResult::class.java)
            .onErrorResume { throwable ->
                if (throwable is DocumentNotFoundException)
                    collection.insert(collectionKey, keys.map { it to 1 }.toMap())
                        .measure(meterRegistry, "set.write.dictionary")
                else
                    Mono.error(throwable)
            }
    }

    fun get(key: String): Mono<Set<T>> {
        return collection.get(key)
            .measure(meterRegistry, "set.read.dictionary")
            .map { it.contentAs(StringKeyedMapTypeRef(Int::class.java)).keys }
            .flatMapIterable { it }
            .flatMap { getOrRemoveEvicted(key, it) }
            .map { it.contentAs(type) }
            .collectList()
            .map { list -> list.toSet() }
            .measure(meterRegistry, "set.read")
    }

    private fun getOrRemoveEvicted(setKey: String, valueKey: String): Mono<GetResult> {
        return collection.get("${setKey}_$valueKey")
            .measure(meterRegistry, "set.read.item")
            .switchIfEmpty(
                removeDictionaryItem(setKey, listOf(valueKey))
                    .measure(meterRegistry, "set.remove.evicteddictionary")
                    .flatMap { Mono.empty() }
            )
    }

    fun remove(key: String, values: Set<T>): Mono<Unit> {
        val valueKeys = values.map { it.identifier() }
        return removeDictionaryItem(key, valueKeys)
            .measure(meterRegistry, "set.remove.dictionary")
            .flatMapIterable {
                valueKeys
            }.flatMap {
                collection.remove("${key}_$it")
                    .measure(meterRegistry, "set.remove.item")
            }.ignoreElements()
            .measure(meterRegistry, "set.remove")
            .map { Unit }
    }

    private fun removeDictionaryItem(key: String, valueKeys: List<String>) =
        Flux.fromIterable(valueKeys)
            .map { MutateInSpec.remove(it) }
            .buffer(MAX_MUTATE_IN_OPERATIONS)
            .concatMap {
                collection.mutateIn(key, it)
            }.collectList()

}

private class StringKeyedMapTypeRef<T>(valueType: Class<T>) : TypeRef<Map<String, T>>() {
    private val type = MapType(String::class.java, valueType)

    override fun type(): Type {
        return type
    }

    private class MapType<K, V>(keyType: Class<K>, valueType: Class<V>) : ParameterizedType {
        val typeArguments = arrayOf(keyType, valueType)

        override fun getActualTypeArguments() = typeArguments

        override fun getRawType() = Map::class.java

        override fun getOwnerType() = null
    }
}

private fun MeterRegistry.increment(metricName: String)
    = counter("cache.couchbase.$metricName").increment()

interface CouchbaseSetEntry {
    fun identifier(): String
}
