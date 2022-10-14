package pl.allegro.tech.couchbasecommons

import com.couchbase.client.core.error.InvalidArgumentException
import com.couchbase.client.java.ClusterOptions
import com.couchbase.client.java.ReactiveCluster
import com.couchbase.client.java.ReactiveCollection
import com.couchbase.client.java.codec.JacksonJsonSerializer
import com.couchbase.client.java.env.ClusterEnvironment
import com.couchbase.client.java.kv.GetOptions
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveLowerBound
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldHaveUpperBound
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.Instant
import org.testcontainers.couchbase.BucketDefinition
import org.testcontainers.couchbase.CouchbaseContainer

class CouchbaseRepositorySpec : StringSpec() {

    private lateinit var reactiveCollection: ReactiveCollection
    private lateinit var collection: CouchbaseCollection
    private val prefix = "prefix"

    override fun beforeSpec(spec: Spec) {
        val bucketName = "mybucket"
        val bucketDefinition = BucketDefinition(bucketName)
        val container = CouchbaseContainer("couchbase/server:6.6.0")
            .withBucket(bucketDefinition)
            .withCredentials("Administrator", "password")
        container.start()

        val cluster = ReactiveCluster.connect(
            container.connectionString,
            ClusterOptions.clusterOptions(container.username, container.password)
                .environment(
                    ClusterEnvironment.builder()
                        .jsonSerializer(JacksonJsonSerializer.create(jacksonObjectMapper()))
                        .build()
                )
        )
        val bucket = cluster.bucket(bucketName)
        reactiveCollection = bucket.defaultCollection()
        collection = ReactiveCouchbaseCollection(reactiveCollection).withPrefix(prefix)
    }

    init {
        "should save object in cache" {
            //GIVEN
            val repository = TypedCouchbaseRepository(collection, DTO::class.java, SimpleMeterRegistry())
            val dto = DTO("key", "body")

            //WHEN
            repository.put(dto.id, dto).block()
            val actualDto = repository.get(dto.id)

            //THEN
            actualDto.block()!!.value shouldBe dto.value

            // AND
            reactiveCollection.exists("prefix_${dto.id}").block()!!.exists() shouldBe true
        }

        "should save object in cache using hashed key when provided ts longer than 250 characters" {
            //GIVEN
            val repository = TypedCouchbaseRepository(collection, DTO::class.java, SimpleMeterRegistry())
            val dto = DTO("id".repeat(250), "body")

            //WHEN
            repository.put(dto.id, dto).block()
            val actualDto = repository.get(dto.id)

            //THEN
            actualDto.block()!!.value shouldBe dto.value

            // AND
            shouldThrow<InvalidArgumentException> {
                reactiveCollection.exists("prefix_${dto.id}").block()
            }
            reactiveCollection.exists("prefix_${dto.id}".sha256()).block()!!.exists() shouldBe true
        }

        "should measure cache read and write" {
            //GIVEN
            val meterRegistry = SimpleMeterRegistry()
            val repository = TypedCouchbaseRepository(collection, DTO::class.java, meterRegistry)
            val writeTimer = meterRegistry.timer("cache.couchbase.key.write")
            val readTimer = meterRegistry.timer("cache.couchbase.key.read")
            val writeActualCount = writeTimer.count()
            val readActualCount = readTimer.count()

            val dto = DTO("key", "body")

            //WHEN
            repository.put(dto.id, dto).block()
            val actualDto = repository.get(dto.id)

            //THEN
            actualDto.block()!!.value shouldBe dto.value
            writeTimer.count() shouldBe writeActualCount + 1
            readTimer.count() shouldBe readActualCount + 1
        }

        "should measure cache errors" {
            //GIVEN
            val meterRegistry = SimpleMeterRegistry()
            val couchbaseCollection = mock<CouchbaseCollection> {
                on { get(any()) } doReturn Mono.error { RuntimeException("timeout?") }
                on { upsert(any(), any()) } doReturn Mono.error { RuntimeException("timeout?") }
            }

            val repository = TypedCouchbaseRepository(couchbaseCollection, DTO::class.java, meterRegistry)
            val writeErrors = meterRegistry.counter("cache.couchbase.key.write_error")
            val readErrors = meterRegistry.counter("cache.couchbase.key.read_error")
            val writeActualCount = writeErrors.count()
            val readActualCount = readErrors.count()

            //WHEN
            repository.put("id", DTO("key", "body")).onErrorResume { _ -> Mono.empty() }.block()
            repository.get("id").onErrorResume { _ -> Mono.empty() }.block()

            //THEN
            writeErrors.count() shouldBe writeActualCount + 1
            readErrors.count() shouldBe readActualCount + 1
        }

        "should save values in set" {
            //GIVEN
            val meterRegistry = SimpleMeterRegistry()
            val writeItemTimer = meterRegistry.timer("cache.couchbase.set.write.item")
            val writeDictionaryTimer = meterRegistry.timer("cache.couchbase.set.write.dictionary")
            val mutateDictionaryTimer = meterRegistry.timer("cache.couchbase.set.mutate.dictionary")
            val writeTimer = meterRegistry.timer("cache.couchbase.set.write")
            val repository = CouchbaseSetRepository(collection, DTO::class.java, meterRegistry)
            val longKey = "dtos1".repeat(250)

            //WHEN
            val ttl = Duration.ofSeconds(60)
            val dtos = (1..30).map { DTO("id_$it", "value_$it") }.toSet()
            repository.add(longKey, dtos, ttl = ttl).block()
            val expiryTime = Instant.now().plus(ttl)

            //EXPECT
            writeItemTimer.count() shouldBe 30
            writeDictionaryTimer.count() shouldBe 1
            mutateDictionaryTimer.count() shouldBe 2
            writeTimer.count() shouldBe 1

            val actualDto = repository.get(longKey).block()!!
            actualDto.size shouldBe 30
            actualDto shouldContainAll (1..30).map { DTO("id_$it", "value_$it") }


            //AND
            val anotherBatch = (21..50).map { DTO("id_$it", "value_$it") }.toSet()
            repository.add(longKey, anotherBatch, ttl = ttl).block()

            //THEN
            writeItemTimer.count() shouldBe 60
            writeDictionaryTimer.count() shouldBe 1
            mutateDictionaryTimer.count() shouldBe 4
            writeTimer.count() shouldBe 2

            val raw = reactiveCollection.get("prefix_$longKey".sha256()).block()!!
            val rawObject = raw.contentAs(Map::class.java)
            val getOptions = GetOptions.getOptions().withExpiry(true)
            val setItems = rawObject.keys
                .map { it as String }
                .map { reactiveCollection.get("prefix_${longKey}_$it".sha256(), getOptions).block() }
            setItems.map { it!!.expiryTime().get() } shouldHaveLowerBound expiryTime.minusSeconds(2)
            setItems.map { it!!.expiryTime().get() } shouldHaveUpperBound expiryTime.plusSeconds(2)
            setItems shouldHaveSize 50
        }

        "should remove evicted values from dictionary" {
            //GIVEN
            val repository = CouchbaseSetRepository(collection, DTO::class.java, SimpleMeterRegistry())

            repository.add("dtos2", DTO("id_1", "value_1"), ttl = Duration.ofSeconds(20)).block()
            repository.add("dtos2", DTO("id_2", "value_2"), ttl = Duration.ofSeconds(10)).block()

            //EXPECT
            var dictionaryKeys = collection.get("dtos2").block()!!
                .contentAs(Map::class.java).keys.map { it as String }
            dictionaryKeys shouldHaveSize 2
            dictionaryKeys.all {
                reactiveCollection.exists("prefix_dtos2_$it").block()!!.exists()
            } shouldBe true

            //AND REMOVE ITEM (MANUAL EVICTION)
            collection.remove("dtos2_${dictionaryKeys[1]}").block()

            //WHEN
            val result = repository.get("dtos2").block()!!

            //THEN
            result shouldHaveSize 1
            dictionaryKeys = reactiveCollection.get("prefix_dtos2").block()!!
                .contentAs(Map::class.java).keys.map { it as String }
            dictionaryKeys shouldHaveSize 1
        }

        "should remove more than 16 items from set" {
            //GIVEN
            val repository = CouchbaseSetRepository(collection, DTO::class.java, SimpleMeterRegistry())
            val ttl = Duration.ofSeconds(60)
            val dtos = (1..30).map { DTO("id_$it", "value_$it") }.toSet()
            repository.add("dtosA", dtos, ttl = ttl).block()

            //EXPECT
            var actualDto = repository.get("dtosA").block()!!
            actualDto.size shouldBe 30
            actualDto shouldContainAll (1..30).map { DTO("id_$it", "value_$it") }

            //WHEN
            repository.remove("dtosA", dtos).block()

            //THEN
            actualDto = repository.get("dtosA").block()!!
            actualDto.size shouldBe 0
        }

        "should remove same value from single set only" {
            //GIVEN
            val repository = CouchbaseSetRepository(collection, DTO::class.java, SimpleMeterRegistry())

            val ttl = Duration.ofSeconds(60)
            val dtos = (1..10).map { DTO("id_$it", "value_$it") }.toSet()
            repository.add("A", dtos, ttl = ttl).block()

            //AND
            repository.add("B", dtos, ttl = ttl).block()

            //WHEN
            repository.remove("A", dtos).block()

            //THEN
            repository.get("A").block()!! shouldHaveSize 0
            repository.get("B").block()!! shouldContainExactly dtos
        }
    }

    data class DTO @JsonCreator constructor(
        @JsonProperty val id: String,
        @JsonProperty val value: String
    ) : CouchbaseSetEntry {
        override fun identifier() = id
    }
}
