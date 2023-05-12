package pl.allegro.tech.couchbasecommons

import com.couchbase.client.java.ReactiveCollection
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.spec.style.StringSpec
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import reactor.core.publisher.Mono

class ReactiveCouchbaseCollectionTest : StringSpec({

    "should hash to long key names" {
        //given
        val reactiveCollection = mock<ReactiveCollection> {
            on { get(any()) } doReturn Mono.empty()
        }

        //when
        ReactiveCouchbaseCollection(reactiveCollection).get(
            "long key with custom letters which makes utf8 byte array longer than string zazółć gęślą jaźń " +
                "long key with custom letters which makes utf8 byte array longer than string zazółć gęślą jaźń " +
                "long key with custom letters which makes utf zazółć gęślą jaźń"
        )

        //then
        verify(reactiveCollection).get("cbaaf74e5a2019cda82fb505f1ecb8ead6144a16a07817f4c09eb32c56b05bcb")
}
})
