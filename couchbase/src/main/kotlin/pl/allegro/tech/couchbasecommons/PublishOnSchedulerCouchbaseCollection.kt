package pl.allegro.tech.couchbasecommons

import com.couchbase.client.java.kv.GetResult
import com.couchbase.client.java.kv.MutateInResult
import com.couchbase.client.java.kv.MutateInSpec
import com.couchbase.client.java.kv.MutationResult
import com.couchbase.client.java.kv.UpsertOptions
import reactor.core.publisher.Mono
import reactor.core.scheduler.Scheduler

class PublishOnSchedulerCouchbaseCollection(
    private val scheduler: Scheduler,
    private val collection: CouchbaseCollection
): CouchbaseCollection {
    override fun get(key: String): Mono<GetResult>
        = collection.get(key).publishOn(scheduler)

    override fun upsert(key: String, value: Any): Mono<MutationResult>
        = collection.upsert(key, value).publishOn(scheduler)

    override fun upsert(key: String, value: Any, options: UpsertOptions): Mono<MutationResult>
        = collection.upsert(key, value, options).publishOn(scheduler)

    override fun mutateIn(key: String, specs: List<MutateInSpec>): Mono<MutateInResult>
        = collection.mutateIn(key, specs).publishOn(scheduler)

    override fun insert(key: String, value: Any): Mono<MutationResult>
        = collection.insert(key, value).publishOn(scheduler)

    override fun remove(key: String): Mono<MutationResult>
        = collection.remove(key).publishOn(scheduler)
}
