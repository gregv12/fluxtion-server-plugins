/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.connector.chronicle.cache;

/**
 * Interface for serializing and deserializing cache values to/from byte arrays.
 * This allows pluggable serialization strategies for ChronicleMapCache.
 *
 * <p>Implementations should handle the conversion of arbitrary objects to byte arrays
 * and back. The serialization strategy determines how objects are stored in Chronicle Map.
 *
 * @see JavaSerializer
 * @see ChronicleMapCache
 */
public interface ValueSerializer {

    /**
     * Serialize an object to a byte array.
     *
     * @param value the object to serialize
     * @return byte array representation of the object
     * @throws SerializationException if serialization fails
     */
    byte[] serialize(Object value);

    /**
     * Deserialize a byte array back to an object.
     *
     * @param <T> the expected type of the deserialized object
     * @param bytes the byte array to deserialize
     * @return the deserialized object
     * @throws SerializationException if deserialization fails
     */
    <T> T deserialize(byte[] bytes);

    /**
     * Exception thrown when serialization or deserialization fails.
     */
    class SerializationException extends RuntimeException {
        public SerializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}