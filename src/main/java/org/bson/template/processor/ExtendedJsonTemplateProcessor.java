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

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonDocumentWriter;
import org.bson.BsonNull;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.codecs.Codec;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;

import java.lang.StringTemplate.Processor;
import java.util.List;

import static java.lang.StringTemplate.interpolate;
import static java.util.Collections.nCopies;
import static org.bson.conversions.Bson.DEFAULT_CODEC_REGISTRY;

/**
 * String template processors for Extended JSON that support interpolation of document keys and document values.
 *
 * <p>
 * An interpolated key must evaluate to a string.
 * </p>
 *
 * <p>
 * An interpolated value must evaluate to a type for which there is a Codec registered in the CodecRegistry.
 * {@link #EXT_JSON} and {@link #EXT_JSON_LIST} use the registry defined by {@link Bson#DEFAULT_CODEC_REGISTRY}.
 * There are also corresponding static methods that allow the application to pass in a registry of its choice.
 * </p>
 */
public final class ExtendedJsonTemplateProcessor {

    /**
     * A template processor that can process a single Extended JSON document.
     */
    public static final Processor<Bson, RuntimeException> EXT_JSON = EXT_JSON(DEFAULT_CODEC_REGISTRY);

    /**
     * A template processor that can process a single Extended JSON array that contains Extended JSON document elements.
     */
    public static final Processor<List<Bson>, RuntimeException> EXT_JSON_LIST = EXT_JSON_LIST(DEFAULT_CODEC_REGISTRY);

    /**
     * A template processor that can process a single Extended JSON document.
     *
     * @param codecRegistry the registry to use to convert each template expression into a string (for keys)
     *                      or a BSON value.
     * @return the template processor
     */
    public static Processor<Bson, RuntimeException> EXT_JSON(CodecRegistry codecRegistry) {
        return Processor.of(stringTemplate -> process(stringTemplate, codecRegistry));
    }

    /**
     * A template processor that can process a single Extended JSON array that contains Extended JSON document elements.
     * 
     * @param codecRegistry the registry to use to convert each template expression into a string (for keys)
     *                      or a BSON value.
     * @return the template processor
     */
    public static Processor<List<Bson>, RuntimeException> EXT_JSON_LIST(CodecRegistry codecRegistry) {
        return Processor.of(stringTemplate -> processList(stringTemplate, codecRegistry));
    }

    // This should be a value that will never be used by a real program.  That's not actually possible since
    // there are no non-associating string values but at least we can detect if the number of sentinels is not as
    // expected and throw, rather than produce an incorrect result
    private static final String SENTINEL = "αឮᝥᦱᜧᐆᄇ⥥℡⡅∟⹀ǖʼោ돜낉᳗ɫ⭦ᬞᎆⰴṝ⍖엔⨈ᣆⱈⵝྌ⹆";

    private static final String SENTINEL_AS_JSON_STRING = "\"" + SENTINEL + "\"";

    private static boolean isSentinel(BsonValue value) {
        return value.isString() && isSentinel(value.asString().getValue());
    }

    private static boolean isSentinel(String value) {
        return value.equals(SENTINEL);
    }

    private static Bson process(StringTemplate stringTemplate, CodecRegistry codecRegistry) {
        return interpolateValues(BsonDocument.parse(
                        interpolate(stringTemplate.fragments(),
                                nCopies(stringTemplate.values().size(), SENTINEL_AS_JSON_STRING))),
                stringTemplate.values(), codecRegistry);
    }

    private static List<Bson> processList(StringTemplate stringTemplate, CodecRegistry codecRegistry) {
        String interpolated = interpolate(stringTemplate.fragments(),
                nCopies(stringTemplate.values().size(), SENTINEL_AS_JSON_STRING));
        return interpolateValues(BsonDocument.parse(
                        "{ w: "
                                + interpolated
                                + "}"), stringTemplate.values(),
                codecRegistry)
                .getArray("w").stream()
                .map(val -> (Bson) val.asDocument())
                .toList();
    }

    private record InterpolationResult(BsonValue resultValue, int numValuesInterpolated) {
    }

    private static BsonDocument interpolateValues(BsonDocument document, List<Object> values,
                                                  CodecRegistry codecRegistry) {
        return interpolateValuesIntoDocument(document, codecRegistry, values, 0).resultValue().asDocument();
    }

    private static InterpolationResult interpolateValues(BsonValue value, CodecRegistry codecRegistry,
                                                         List<Object> values, int startIndex) {
        if (isSentinel(value)) {
            return new InterpolationResult(asBsonValue(getValue(values, startIndex), codecRegistry), 1);
        } else if (value.isDocument()) {
            return interpolateValuesIntoDocument(value.asDocument(), codecRegistry, values, startIndex);
        } else if (value.isArray()) {
            return interpolateValuesIntoArray(value.asArray(), codecRegistry, values, startIndex);
        } else {
            return new InterpolationResult(value, 0);
        }
    }

    private static InterpolationResult interpolateValuesIntoDocument(BsonDocument document, CodecRegistry codecRegistry,
                                                                     List<Object> values, int startIndex) {
        int numValuesInterpolated = 0;
        BsonDocument resultDocument = new BsonDocument();
        for (var entry : document.entrySet()) {
            var keyInterpolationResult = interpolateValues(new BsonString(entry.getKey()), codecRegistry,
                    values, startIndex + numValuesInterpolated);
            numValuesInterpolated += keyInterpolationResult.numValuesInterpolated();

            var valueInterpolationResult = interpolateValues(entry.getValue(), codecRegistry, values,
                    startIndex + numValuesInterpolated);
            numValuesInterpolated += valueInterpolationResult.numValuesInterpolated();

            resultDocument.append(
                    keyInterpolationResult.resultValue().asString().getValue(),
                    valueInterpolationResult.resultValue());
        }
        return new InterpolationResult(resultDocument, numValuesInterpolated);
    }

    private static InterpolationResult interpolateValuesIntoArray(BsonArray array, CodecRegistry codecRegistry,
                                                                  List<Object> values, int startIndex) {
        int numValuesInterpolated = 0;
        for (int i = 0; i < array.size(); i++) {
            var interpolationResult = interpolateValues(array.get(i), codecRegistry, values,
                    startIndex + numValuesInterpolated);
            array.set(i, interpolationResult.resultValue());
            numValuesInterpolated += interpolationResult.numValuesInterpolated();
        }
        return new InterpolationResult(array, numValuesInterpolated);
    }

    private static Object getValue(List<Object> values, int index) {
        if (index >= values.size()) {
            throw new IllegalArgumentException("Uh oh.  Looks like the string template contains the super-rare"
                    + " sentinel value of " + SENTINEL_AS_JSON_STRING);
        }
        return values.get(index);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BsonValue asBsonValue(Object value, CodecRegistry codecRegistry) {
        if (value == null) {
            return BsonNull.VALUE;
        }

        BsonDocument wrapper = new BsonDocument();
        BsonDocumentWriter writer = new BsonDocumentWriter(wrapper);
        writer.writeStartDocument();
        writer.writeName("w");
        Codec codec = codecRegistry.get(value.getClass());
        codec.encode(writer, value, EncoderContext.builder().build());
        writer.writeEndDocument();
        return wrapper.get("w");
    }
}
