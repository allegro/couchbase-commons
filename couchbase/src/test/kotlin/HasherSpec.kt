import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import pl.allegro.tech.couchbasecommons.sha256

class HasherSpec : StringSpec({
    "should hash string" {
        // GIVEN
        val input = "zażółć gęślą jaźń"

        // WHEN
        val result = input.sha256()

        // THEN
        result shouldBe "ab4e973a71cf9dd8a6d0d9b8030029b219b6e162d1dbdf0ad0ebf0dd698d6057"
    }

})
