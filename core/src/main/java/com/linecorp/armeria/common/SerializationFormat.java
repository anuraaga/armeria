/*
 * Copyright 2015 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.linecorp.armeria.common;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.linecorp.armeria.common.MediaType.create;
import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.common.base.Ascii;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;

/**
 * Serialization format of a remote procedure call and its reply.
 */
public final class SerializationFormat implements Comparable<SerializationFormat> {

    private static final BiMap<String, SerializationFormat> uriTextToFormats;
    private static final Set<SerializationFormat> values;

    /**
     * A {@link Multimap} of the {@link MediaType}s whose parameters removed and {@link SerializationFormat}s.
     * We maintain this multi-map to ensure that no duplicate media types are registered.
     */
    private static final Multimap<MediaType, SerializationFormat> simplifiedMediaTypeToFormats;

    /**
     * No serialization format. Used when no serialization/deserialization is desired.
     */
    public static final SerializationFormat NONE;

    /**
     * Unknown serialization format. Used when some serialization format is desired but the server
     * failed to understand or recognize it.
     */
    public static final SerializationFormat UNKNOWN;

    /**
     * @deprecated Use {@code ThriftSerializationFormats.BINARY}. Note that the value of this field will be
     *             {@code null} if {@code armeria-thrift} module is not loaded.
     */
    @Deprecated
    public static final SerializationFormat THRIFT_BINARY;

    /**
     * @deprecated Use {@code ThriftSerializationFormats.COMPACT}. Note that the value of this field will be
     *             {@code null} if {@code armeria-thrift} module is not loaded.
     */
    @Deprecated
    public static final SerializationFormat THRIFT_COMPACT;

    /**
     * @deprecated Use {@code ThriftSerializationFormats.JSON}. Note that the value of this field will be
     *             {@code null} if {@code armeria-thrift} module is not loaded.
     */
    @Deprecated
    public static final SerializationFormat THRIFT_JSON;

    /**
     * @deprecated Use {@code ThriftSerializationFormats.TEXT}. Note that the value of this field will be
     *             {@code null} if {@code armeria-thrift} module is not loaded.
     */
    @Deprecated
    public static final SerializationFormat THRIFT_TEXT;

    private static final Set<SerializationFormat> THRIFT_FORMATS;

    static {
        BiMap<String, SerializationFormat> mutableUriTextToFormats = HashBiMap.create();
        Multimap<MediaType, SerializationFormat> mutableSimplifiedMediaTypeToFormats = HashMultimap.create();

        // Register the core formats first.
        NONE = register(mutableUriTextToFormats, mutableSimplifiedMediaTypeToFormats,
                        new SerializationFormatProvider.Entry("none", create("application", "x-none")));
        UNKNOWN = register(mutableUriTextToFormats, mutableSimplifiedMediaTypeToFormats,
                           new SerializationFormatProvider.Entry(
                                   "unknown", create("application", "x-unknown")));

        // Load all serialization formats from the providers.
        ServiceLoader.load(SerializationFormatProvider.class,
                           SerializationFormatProvider.class.getClassLoader())
                     .forEach(p -> p.entries().forEach(e -> register(mutableUriTextToFormats,
                                                                     mutableSimplifiedMediaTypeToFormats, e)));

        uriTextToFormats = ImmutableBiMap.copyOf(mutableUriTextToFormats);
        values = uriTextToFormats.values();
        simplifiedMediaTypeToFormats = ImmutableMultimap.copyOf(mutableSimplifiedMediaTypeToFormats);

        // Backward compatibility stuff
        SerializationFormat tbinary = null;
        SerializationFormat tcompact = null;
        SerializationFormat tjson = null;
        SerializationFormat ttext = null;
        Set<SerializationFormat> thriftFormats = null;
        try {
            tbinary = of("tbinary");
            tcompact = of("tcompact");
            tjson = of("tjson");
            ttext = of("ttext");
            thriftFormats = ImmutableSet.of(tbinary, tcompact, tjson, ttext);
        } catch (IllegalArgumentException e) {
            // ThriftSerializationFormatProvider is not loaded.
        }

        THRIFT_BINARY = tbinary;
        THRIFT_COMPACT = tcompact;
        THRIFT_JSON = tjson;
        THRIFT_TEXT = ttext;
        THRIFT_FORMATS = thriftFormats;
    }

    private static SerializationFormat register(
            BiMap<String, SerializationFormat> uriTextToFormats,
            Multimap<MediaType, SerializationFormat> simplifiedMediaTypeToFormats,
            SerializationFormatProvider.Entry entry) {

        checkState(!uriTextToFormats.containsKey(entry.uriText),
                   "serialization format registered already: ", entry.uriText);

        final SerializationFormat value = new SerializationFormat(
                entry.uriText, entry.primaryMediaType, entry.allMediaTypes);
        for (MediaType type : entry.allMediaTypes) {
            checkMediaType(simplifiedMediaTypeToFormats, type);
        }

        uriTextToFormats.put(entry.uriText, value);
        for (MediaType type : entry.allMediaTypes) {
            simplifiedMediaTypeToFormats.put(type.withoutParameters(), value);
        }

        return value;
    }

    /**
     * @deprecated Use {@code ThriftSerializationFormats.values()}.
     *
     * @throws IllegalStateException if {@code armeria-thrift} module is not loaded
     */
    @Deprecated
    public static Set<SerializationFormat> ofThrift() {
        if (THRIFT_FORMATS == null) {
            throw new IllegalStateException("Thrift support not available");
        }

        return THRIFT_FORMATS;
    }

    /**
     * Makes sure the specified {@link MediaType} or its compatible one is registered already.
     */
    private static void checkMediaType(Multimap<MediaType, SerializationFormat> simplifiedMediaTypeToFormats,
                                       MediaType mediaType) {
        final MediaType simplifiedMediaType = mediaType.withoutParameters();
        for (SerializationFormat format : simplifiedMediaTypeToFormats.get(simplifiedMediaType)) {
            for (MediaType registeredMediaType : format.mediaTypes()) {
                checkState(!registeredMediaType.is(mediaType) && !mediaType.is(registeredMediaType),
                           "media type registered already: ", mediaType);
            }
        }
    }

    /**
     * Returns all available {@link SessionProtocol}s.
     */
    public static Set<SerializationFormat> values() {
        return values;
    }

    /**
     * Returns the {@link SerializationFormat} with the specified {@link #uriText()}.
     *
     * @throws IllegalArgumentException if there's no such {@link SerializationFormat}
     */
    public static SerializationFormat of(String uriText) {
        uriText = Ascii.toLowerCase(requireNonNull(uriText, "uriText"));
        final SerializationFormat value = uriTextToFormats.get(uriText);
        checkArgument(value != null, "unknown serialization format: ", uriText);
        return value;
    }

    /**
     * Finds the {@link SerializationFormat} with the specified {@link #uriText()}.
     */
    public static Optional<SerializationFormat> find(String uriText) {
        uriText = Ascii.toLowerCase(requireNonNull(uriText, "uriText"));
        return Optional.ofNullable(uriTextToFormats.get(uriText));
    }

    /**
     * Finds the {@link SerializationFormat} which is accepted by the specified {@link MediaType}.
     */
    public static Optional<SerializationFormat> find(MediaType mediaType) {
        requireNonNull(mediaType, "mediaType");
        for (SerializationFormat f : simplifiedMediaTypeToFormats.get(mediaType.withoutParameters())) {
            if (f.isAccepted(mediaType)) {
                return Optional.of(f);
            }
        }

        return Optional.empty();
    }

    /**
     * @deprecated Use {@link #find(MediaType)}.
     */
    @Deprecated
    public static Optional<SerializationFormat> fromMediaType(@Nullable String mediaType) {
        if (mediaType == null) {
            return Optional.empty();
        }

        try {
            return find(MediaType.parse(mediaType));
        } catch (IllegalArgumentException e) {
            // Malformed media type
            return Optional.empty();
        }
    }

    private final String uriText;
    private final MediaType primaryMediaType;
    private final Set<MediaType> allMediaTypes;

    private SerializationFormat(String uriText, MediaType primaryMediaType, Set<MediaType> allMediaTypes) {
        this.uriText = uriText;
        this.primaryMediaType = primaryMediaType;
        this.allMediaTypes = ImmutableSet.copyOf(allMediaTypes);
    }

    /**
     * Returns the textual representation of this format for use in a {@link Scheme}.
     */
    public String uriText() {
        return uriText;
    }

    /**
     * Returns the primary {@link MediaType} of this format.
     */
    public MediaType mediaType() {
        return primaryMediaType;
    }

    /**
     * Returns the media types accepted by this format.
     */
    public Set<MediaType> mediaTypes() {
        return allMediaTypes;
    }

    /**
     * Returns whether the specified {@link MediaType} is accepted by any of the {@link #mediaTypes()}
     * defined by this format.
     */
    public boolean isAccepted(MediaType mediaType) {
        requireNonNull(mediaType, "mediaType");

        // Similar to what MediaType.is(MediaType) does except that
        // this one compares the parameters case-insensitively.
        for (MediaType type : allMediaTypes) {
            if (!type.type().equals(mediaType.type()) ||
                !type.subtype().equals(mediaType.subtype())) {
                continue;
            }

            final Map<String, List<String>> requiredParameters = type.parameters();
            final Map<String, List<String>> actualParameters = mediaType.parameters();
            if (containsAllParameters(requiredParameters, actualParameters)) {
                return true;
            }
        }

        return false;
    }

    private static boolean containsAllParameters(Map<String, List<String>> requiredParameters,
                                                 Map<String, List<String>> actualParameters) {

        if (requiredParameters.isEmpty()) {
            return true;
        }

        for (Entry<String, List<String>> requiredEntry : requiredParameters.entrySet()) {
            final List<String> requiredValues = requiredEntry.getValue();
            final List<String> actualValues = actualParameters.get(requiredEntry.getKey());

            assert !requiredValues.isEmpty();
            if (actualValues == null || actualValues.isEmpty()) {
                // Does not contain any required values.
                return false;
            }

            if (!containsAllRequiredValues(requiredValues, actualValues)) {
                return false;
            }
        }

        return true;
    }

    private static boolean containsAllRequiredValues(List<String> requiredValues, List<String> actualValues) {
        final int numRequiredValues = requiredValues.size();
        for (int i = 0; i < numRequiredValues; i++) {
            if (!containsRequiredValue(requiredValues.get(i), actualValues)) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsRequiredValue(String requiredValue, List<String> actualValues) {
        final int numActualValues = actualValues.size();
        for (int i = 0; i < numActualValues; i++) {
            if (Ascii.equalsIgnoreCase(requiredValue, actualValues.get(i))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj;
    }

    @Override
    public int compareTo(SerializationFormat o) {
        return uriText.compareTo(o.uriText);
    }

    @Override
    public String toString() {
        return uriText;
    }
}
