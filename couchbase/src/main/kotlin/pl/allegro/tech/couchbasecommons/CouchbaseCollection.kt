package pl.allegro.tech.couchbasecommons

import com.couchbase.client.java.kv.GetResult
import com.couchbase.client.java.kv.MutateInResult
import com.couchbase.client.java.kv.MutateInSpec
import com.couchbase.client.java.kv.MutationResult
import com.couchbase.client.java.kv.UpsertOptions
import reactor.core.publisher.Mono

interface CouchbaseCollection {
    fun get(key: String): Mono<GetResult>
    fun upsert(key: String, value: Any): Mono<MutationResult>
    fun upsert(key: String, value: Any, options: UpsertOptions): Mono<MutationResult>
    fun mutateIn(key: String, specs: List<MutateInSpec>): Mono<MutateInResult>
    fun insert(key: String, value: Any): Mono<MutationResult>
    fun remove(key: String): Mono<MutationResult>
}
