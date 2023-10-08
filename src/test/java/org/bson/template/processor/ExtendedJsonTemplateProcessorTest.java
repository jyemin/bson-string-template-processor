/*
 * Copyright 2023-present MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.bson.template.processor;

import org.bson.BsonBinary;
import org.bson.BsonDocument;
import org.bson.codecs.configuration.CodecRegistries;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.bson.BsonDocument.parse;
import static org.bson.UuidRepresentation.STANDARD;
import static org.bson.conversions.Bson.DEFAULT_CODEC_REGISTRY;
import static org.bson.template.processor.ExtendedJsonTemplateProcessor.EXT_JSON;
import static org.bson.template.processor.ExtendedJsonTemplateProcessor.EXT_JSON_LIST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


class ExtendedJsonTemplateProcessorTest {

    @Test
    public void testKeyInterpolation() {
        var result = EXT_JSON. "{ \{ "k1" }: 1 }" ;
        assertEquals(parse("{k1 : 1}"), result.toBsonDocument());
    }

    @Test
    public void testValueInterpolation() {
        var result = EXT_JSON. "{ k1: \{ 1 } }" ;
        assertEquals(parse("{k1 : 1}"), result.toBsonDocument());
    }

    @Test
    public void testNullValueInterpolation() {
        var result = EXT_JSON. "{ k1: \{ null } }" ;
        assertEquals(parse("{k1 : null}"), result.toBsonDocument());
    }

    @Test
    public void testNestedDocumentInterpolation() {
        var result = EXT_JSON. "{ k1: {k2 : \{ 1 }, k3: \{ 2 } }}" ;
        assertEquals(parse("{k1 : {k2: 1, k3: 2}}"), result.toBsonDocument());
    }

    @Test
    public void testNestedArrayInterpolation() {
        var result = EXT_JSON. "{ k1: [\{ 1 }, \{ 2 } ]}" ;
        assertEquals(parse("{k1 : [1, 2]}"), result.toBsonDocument());
    }

    @Test
    public void testBsonListInterpolation() {
        var result = EXT_JSON_LIST."[{k1: \{1}}, {k1: \{2}}]";
        assertEquals(List.of(parse("{k1: 1}"), parse("{k1: 2}")), result);
    }

    @Test
    public void testSuppliedCodecRegistry() {
        var uuid = UUID.randomUUID();
        var docResult = EXT_JSON(CodecRegistries.withUuidRepresentation(DEFAULT_CODEC_REGISTRY, STANDARD))
                . "{ k1: \{ uuid}}" ;
        assertEquals(new BsonDocument("k1", new BsonBinary(uuid, STANDARD)), docResult);

        var listResult = EXT_JSON_LIST(CodecRegistries.withUuidRepresentation(DEFAULT_CODEC_REGISTRY, STANDARD))
                . "[ { k1: \{ uuid}} ]" ;
        assertEquals(List.of(new BsonDocument("k1", new BsonBinary(uuid, STANDARD))), listResult);
    }

    @Test
    public void testSentinelDetection() {
        assertThrows(IllegalArgumentException.class,
                () -> {
                    var res = EXT_JSON. "{ k1: \{ 1 }, k2: \"αឮᝥᦱᜧᐆᄇ⥥℡⡅∟⹀ǖʼោ돜낉᳗ɫ⭦ᬞᎆⰴṝ⍖엔⨈ᣆⱈⵝྌ⹆\" }";
                });
    }
}