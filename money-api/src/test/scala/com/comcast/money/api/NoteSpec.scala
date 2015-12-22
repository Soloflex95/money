/*
 * Copyright 2012-2015 Comcast Cable Communications Management, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.comcast.money.api

import org.scalatest.{ Matchers, WordSpec }
import scala.collection.JavaConversions._
import scala.collection.JavaConverters._

class NoteSpec extends WordSpec with Matchers {

  "A Note" should {
    "create String notes" in {
      val note = Note.of("foo", "bar")

      note.isSticky shouldBe false
      note.name shouldBe "foo"
      note.value shouldBe "bar"
      note.timestamp should not be 0
    }

    "create Long notes" in {
      val note = Note.of("foo", 1L)

      note.isSticky shouldBe false
      note.name shouldBe "foo"
      note.value shouldBe 1L
      note.timestamp should not be 0
    }

    "create Double notes" in {
      val note = Note.of("foo", 2.2)

      note.isSticky shouldBe false
      note.name shouldBe "foo"
      note.value shouldBe 2.2
      note.timestamp should not be 0
    }

    "create Boolean notes" in {
      val note = Note.of("foo", true)

      note.isSticky shouldBe false
      note.name shouldBe "foo"
      note.value shouldBe true
      note.timestamp should not be 0
    }

    "create String notes with propagated" in {
      val note = Note.of("foo", "bar", true)

      note.isSticky shouldBe true
      note.name shouldBe "foo"
      note.value shouldBe "bar"
      note.timestamp should not be 0
    }

    "create Long notes with propagated" in {
      val note = Note.of("foo", 1L, true)

      note.isSticky shouldBe true
      note.name shouldBe "foo"
      note.value shouldBe 1L
      note.timestamp should not be 0
    }

    "create Double notes with propagated" in {
      val note = Note.of("foo", 2.2, true)

      note.isSticky shouldBe true
      note.name shouldBe "foo"
      note.value shouldBe 2.2
      note.timestamp should not be 0
    }

    "create Boolean notes with propagated" in {
      val note = Note.of("foo", true, true)

      note.isSticky shouldBe true
      note.name shouldBe "foo"
      note.value shouldBe true
      note.timestamp should not be 0
    }
  }
}