package pl.allegro.tech.couchbasecommons

import com.couchbase.client.core.error.DocumentNotFoundException
import com.couchbase.client.java.kv.GetResult
import com.couchbase.client.java.kv.InsertOptions
import com.couchbase.client.java.kv.MutateInOptions
import com.couchbase.client.java.kv.MutateInResult
import com.couchbase.client.java.kv.MutateInSpec
import com.couchbase.client.java.kv.MutationResult
import com.couchbase.client.java.kv.UpsertOptions
import reactor.core.publisher.Mono

class KeyPrefixingCollection(
    private val prefix: String,
    private val collection: CouchbaseCollection
): CouchbaseCollection {
    override fun get(key: String): Mono<GetResult>
        = collection
        .get(prefix(key))
        .onErrorResume { error ->
            when(error) {
                is DocumentNotFoundException -> Mono.empty()
                else -> Mono.error(ErrorGettingPrefixedKey(prefix, key, error))
            }
        }.retry(1)

    override fun upsert(key: String, value: Any): Mono<MutationResult>
        = collection.upsert(prefix(key), value)

    override fun upsert(key: String, value: Any, options: UpsertOptions): Mono<MutationResult>
        = collection.upsert(prefix(key), value, options)

    override fun mutateIn(key: String, specs: List<MutateInSpec>, options: MutateInOptions): Mono<MutateInResult>
        = collection.mutateIn(prefix(key), specs, options)

    override fun insert(key: String, value: Any, options: InsertOptions): Mono<MutationResult>
        = collection.insert(prefix(key), value, options)

    override fun remove(key: String): Mono<MutationResult>
        = collection.remove(prefix(key))

    private fun prefix(key: String): String
        = "${prefix}_$key"
}

class ErrorGettingPrefixedKey(prefix: String, key: String, error: Throwable)
    : RuntimeException(
    "Error getting prefixed key. Prefix: $prefix, key: $key",
    error
)

fun CouchbaseCollection.withPrefix(prefix: String)
    = KeyPrefixingCollection(prefix, this)
