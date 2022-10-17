package pl.allegro.tech.couchbasecommons

import com.couchbase.client.java.ReactiveCollection
import com.couchbase.client.java.kv.GetResult
import com.couchbase.client.java.kv.InsertOptions
import com.couchbase.client.java.kv.MutateInOptions
import com.couchbase.client.java.kv.MutateInResult
import com.couchbase.client.java.kv.MutateInSpec
import com.couchbase.client.java.kv.MutationResult
import com.couchbase.client.java.kv.UpsertOptions
import reactor.core.publisher.Mono

private const val MAX_KEY_LENGTH = 250

class ReactiveCouchbaseCollection(
    private val collection: ReactiveCollection
): CouchbaseCollection {
    override fun get(key: String): Mono<GetResult>
        = collection.get(prepareKey(key))

    override fun upsert(key: String, value: Any): Mono<MutationResult>
        = collection.upsert(prepareKey(key), value)

    override fun upsert(key: String, value: Any, options: UpsertOptions): Mono<MutationResult>
        = collection.upsert(prepareKey(key), value, options)

    override fun mutateIn(key: String, specs: List<MutateInSpec>, options: MutateInOptions): Mono<MutateInResult>
        = collection.mutateIn(prepareKey(key), specs, options)

    override fun insert(key: String, value: Any, options: InsertOptions): Mono<MutationResult>
        = collection.insert(prepareKey(key), value, options)

    override fun remove(key: String): Mono<MutationResult>
        = collection.remove(prepareKey(key))

    private fun prepareKey(key: String)
        = if (key.length > MAX_KEY_LENGTH) {
        key.sha256()
    } else {
        key
    }
}
