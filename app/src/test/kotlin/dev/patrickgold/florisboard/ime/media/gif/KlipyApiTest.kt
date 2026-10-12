/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.media.gif

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.IOException

class KlipyApiTest : FunSpec({
    fun fileJson(name: String, width: Int, height: Int) = """
        {"gif":{"url":"https://static.example.com/$name.gif","width":$width,"height":$height,"size":1},
        "webp":{"url":"https://static.example.com/$name.webp","width":$width,"height":$height,"size":1}}
    """.trimIndent()

    test("page of GIFs is parsed") {
        val page = KlipyApi.parsePage(
            """
            {"result":true,"data":{"data":[
            {"id":2770058343398499,"slug":"good-night","title":"Good Night","type":"gif","tags":[],
            "blur_preview":"data:image/jpeg;base64,AAAA",
            "file":{"hd":${fileJson("hd", 498, 246)},"md":${fileJson("md", 498, 246)},
            "sm":${fileJson("sm", 220, 109)},"xs":${fileJson("xs", 183, 90)}}},
            {"id":"2","title":"Only large","type":"gif","file":{"hd":${fileJson("hd2", 400, 400)}}}
            ],"current_page":2,"per_page":24,"has_next":true,"meta":{"item_min_width":80}}}
            """.trimIndent()
        )

        page.page shouldBe 2
        page.hasNext shouldBe true
        page.gifs shouldBe listOf(
            Gif(
                id = "2770058343398499",
                title = "Good Night",
                previewUrl = "https://static.example.com/sm.gif",
                previewWidth = 220,
                previewHeight = 109,
                url = "https://static.example.com/md.gif",
            ),
            Gif(
                id = "2",
                title = "Only large",
                previewUrl = "https://static.example.com/hd2.gif",
                previewWidth = 400,
                previewHeight = 400,
                url = "https://static.example.com/hd2.gif",
            ),
        )
    }

    test("items which are not GIFs or lack files are skipped") {
        val page = KlipyApi.parsePage(
            """
            {"result":true,"data":{"data":[
            {"id":1,"title":"Ad","type":"ad","file":{"md":${fileJson("ad", 10, 10)}}},
            {"id":2,"title":"No files","type":"gif"},
            {"id":3,"title":"No gif format","type":"gif","file":{"md":{"webp":{"url":"https://static.example.com/a.webp"}}}},
            {"id":4,"title":"Kept","type":"gif","file":{"md":${fileJson("kept", 10, 20)}}},
            {"id":4,"title":"Duplicate","type":"gif","file":{"md":${fileJson("kept", 10, 20)}}}
            ],"current_page":1,"has_next":false}}
            """.trimIndent()
        )

        page.gifs.map { it.title } shouldBe listOf("Kept")
        page.gifs.single().aspectRatio shouldBe 0.5f
        page.hasNext shouldBe false
    }

    test("error responses are rejected") {
        shouldThrow<IOException> { KlipyApi.parsePage("""{"result":false,"errors":{"message":["nope"]}}""") }
        shouldThrow<IOException> { KlipyApi.parsePage("""{"something":"else"}""") }
    }
})
