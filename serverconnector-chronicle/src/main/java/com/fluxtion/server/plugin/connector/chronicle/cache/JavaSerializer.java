/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.fluxtion.server.plugin.connector.chronicle.cache;

import java.io.*;

/**
 * Default ValueSerializer implementation using Java's built-in serialization.
 *
 * <p>This serializer uses {@link ObjectOutputStream} and {@link ObjectInputStream}
 * to serialize and deserialize objects. Objects must implement {@link Serializable}.
 *
 * <p><b>Note:</b> Java serialization is convenient but not the most efficient.
 * For better performance, consider implementing custom serializers using
 * more efficient formats (e.g., Protocol Buffers, MessagePack, FST, Kryo).
 *
 * @see ValueSerializer
 */
public class JavaSerializer implements ValueSerializer {

    @Override
    public byte[] serialize(Object value) {
        if (value == null) {
            return null;
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(value);
            oos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new SerializationException("Failed to serialize object of type: " + value.getClass().getName(), e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T deserialize(byte[] bytes) {
        if (bytes == null) {
            return null;
        }

        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
             ObjectInputStream ois = new ObjectInputStream(bais)) {
            return (T) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new SerializationException("Failed to deserialize object", e);
        }
    }
}